/* Copyright 2017-18, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.symphony.mt.models.transformer

import org.platanios.symphony.mt.models.{ModelConstructionContext, Sequences}
import org.platanios.symphony.mt.models.Transformation.Decoder
import org.platanios.symphony.mt.models.decoders.{BasicDecoder, BeamSearchDecoder, OutputLayer, ProjectionToWords}
import org.platanios.symphony.mt.models.helpers.Common
import org.platanios.symphony.mt.models.transformer.helpers.Attention.MultiHeadAttentionCache
import org.platanios.symphony.mt.models.transformer.helpers._
import org.platanios.symphony.mt.vocabulary.Vocabulary
import org.platanios.tensorflow.api._
import org.platanios.tensorflow.api.tf.{RNNCell, RNNTuple}

/**
  * @author Emmanouil Antonios Platanios
  */
class TransformerDecoder[T: TF : IsHalfOrFloatOrDouble](
    val numUnits: Int,
    val numLayers: Int,
    val useSelfAttentionProximityBias: Boolean = false,
    val positionEmbeddings: PositionEmbeddings = FixedSinusoidPositionEmbeddings(1.0f, 1e4f),
    val postPositionEmbeddingsDropout: Float = 0.1f,
    val layerPreprocessors: Seq[LayerProcessor] = Seq(
      Normalize(LayerNormalization(), 1e-6f)),
    val layerPostprocessors: Seq[LayerProcessor] = Seq(
      Dropout(0.1f, broadcastAxes = Set.empty),
      AddResidualConnection),
    val removeFirstLayerResidualConnection: Boolean = false,
    val attentionKeysDepth: Int = 128,
    val attentionValuesDepth: Int = 128,
    val attentionNumHeads: Int = 8,
    val selfAttention: Attention = DotProductAttention(0.1f, Set.empty, "DotProductAttention"),
    val feedForwardLayer: FeedForwardLayer = DenseReLUDenseFeedForwardLayer(256, 128, 0.1f, Set.empty, "FeedForward"),
    val useEncoderDecoderAttentionCache: Boolean = true,
    val outputLayer: OutputLayer = ProjectionToWords
) extends Decoder[EncodedSequences[T]] {
  override def applyTrain(
      encodedSequences: EncodedSequences[T]
  )(implicit context: ModelConstructionContext): Sequences[Float] = {
    // TODO: What if no target sequences are provided?
    val tgtSequences = context.tgtSequences.get

    // Shift the target sequence one step forward so the decoder learns to output the next word.
    val tgtBosId = tf.constant[Int](Vocabulary.BEGIN_OF_SEQUENCE_TOKEN_ID)
    val batchSize = tf.shape(tgtSequences.sequences).slice(0)
    val shiftedTgtSequences = tf.concatenate(Seq(
      tf.fill[Int, Int](tf.stack[Int](Seq(batchSize, 1)))(tgtBosId),
      tgtSequences.sequences), axis = 1)
    val shiftedTgtSequencesMaxLength = tf.shape(shiftedTgtSequences).slice(1)

    // Embed the target sequences.
    val embeddedTgtSequences = embeddings(shiftedTgtSequences)

    // Perform some pre-processing to the token embeddings sequence.
    var decoderSelfAttentionBias = Attention.attentionBiasLowerTriangular(shiftedTgtSequencesMaxLength)
    if (useSelfAttentionProximityBias)
      decoderSelfAttentionBias += Attention.attentionBiasProximal(shiftedTgtSequencesMaxLength)
    var decoderInputSequences = positionEmbeddings.addTo(embeddedTgtSequences)
    if (context.mode.isTraining)
      decoderInputSequences = tf.dropout(decoderInputSequences, 1.0f - postPositionEmbeddingsDropout)

    // Project the decoder input sequences, if necessary.
    val wordEmbeddingsSize = context.parameterManager.wordEmbeddingsType.embeddingsSize
    if (wordEmbeddingsSize != numUnits) {
      val projectionWeights = context.parameterManager.get[T](
        "ProjectionToDecoderNumUnits", Shape(wordEmbeddingsSize, numUnits))
      decoderInputSequences = Common.matrixMultiply(decoderInputSequences, projectionWeights)
    }

    // Project the encoder output, if necessary.
    var encoderOutput = encodedSequences.states
    if (numUnits != encoderOutput.shape(-1)) {
      val projectionWeights = context.parameterManager.get[T](
        "ProjectionToDecoderNumUnits", Shape(encoderOutput.shape(-1), numUnits))
      encoderOutput = Common.matrixMultiply(encoderOutput, projectionWeights)
    }

    val encodedSequencesMaxLength = tf.shape(encoderOutput).slice(1)
    val inputPadding = tf.logicalNot(
      tf.sequenceMask(encodedSequences.lengths, encodedSequencesMaxLength, name = "Padding")).toFloat
    val encoderDecoderAttentionBias = Attention.attentionBiasIgnorePadding(inputPadding)

    // Obtain the output projection layer weights.
    val outputLayer = this.outputLayer(numUnits)

    // Pre-compute the encoder-decoder attention keys and values.
    val encoderDecoderAttentionCache = (0 until numLayers).map(layer => {
      tf.variableScope(s"Layer$layer/EncoderDecoderAttention/Cache") {
        val encoderDecoderAttentionKeys = Attention.computeAttentionComponent(
          encoderOutput, attentionKeysDepth, name = "K").toFloat
        val encoderDecoderAttentionValues = Attention.computeAttentionComponent(
          encoderOutput, attentionValuesDepth, name = "V").toFloat
        MultiHeadAttentionCache(
          keys = Attention.splitHeads(encoderDecoderAttentionKeys, attentionNumHeads),
          values = Attention.splitHeads(encoderDecoderAttentionValues, attentionNumHeads))
      }
    })

    // Finally, apply the decoding model.
    val (decodedSequences, _) = decode(
      encoderOutput = encoderOutput.toFloat,
      decoderInputSequences = decoderInputSequences,
      decoderSelfAttentionBias = decoderSelfAttentionBias,
      decoderSelfAttentionCache = None,
      encoderDecoderAttentionBias = encoderDecoderAttentionBias,
      encoderDecoderAttentionCache = Some(encoderDecoderAttentionCache))
    val outputSequences = outputLayer(decodedSequences)
    Sequences(outputSequences.toFloat, encodedSequences.lengths)
  }

  override def applyInfer(
      encodedSequences: EncodedSequences[T]
  )(implicit context: ModelConstructionContext): Sequences[Int] = {
    val wordEmbeddingsSize = context.parameterManager.wordEmbeddingsType.embeddingsSize

    // Project the encoder output, if necessary.
    var encoderOutput = encodedSequences.states
    if (numUnits != encoderOutput.shape(-1)) {
      val projectionWeights = context.parameterManager.get[T](
        "ProjectionToDecoderNumUnits", Shape(encoderOutput.shape(-1), numUnits))
      encoderOutput = Common.matrixMultiply(encoderOutput, projectionWeights)
    }

    // Determine the maximum allowed sequence length to consider while decoding.
    val maxDecodingLength = {
      if (!context.mode.isTraining && context.dataConfig.tgtMaxLength != -1)
        tf.constant(context.dataConfig.tgtMaxLength)
      else
        tf.round(tf.max(encodedSequences.lengths).toFloat * context.inferenceConfig.maxDecodingLengthFactor).toInt
    }

    val positionEmbeddings = this.positionEmbeddings.get(maxDecodingLength + 1, wordEmbeddingsSize).castTo[T]
    var decoderSelfAttentionBias = Attention.attentionBiasLowerTriangular(maxDecodingLength)
    if (useSelfAttentionProximityBias)
      decoderSelfAttentionBias += Attention.attentionBiasProximal(maxDecodingLength)

    // Pre-compute the encoder-decoder attention keys and values.
    val encoderDecoderAttentionCache = (0 until numLayers).map(layer => {
      tf.variableScope(s"Layer$layer/EncoderDecoderAttention/Cache") {
        val encoderDecoderAttentionKeys = Attention.computeAttentionComponent(
          encoderOutput, attentionKeysDepth, name = "K").toFloat
        val encoderDecoderAttentionValues = Attention.computeAttentionComponent(
          encoderOutput, attentionValuesDepth, name = "V").toFloat
        MultiHeadAttentionCache(
          keys = Attention.splitHeads(encoderDecoderAttentionKeys, attentionNumHeads),
          values = Attention.splitHeads(encoderDecoderAttentionValues, attentionNumHeads))
      }
    })

    // Create the decoder RNN cell.
    val zero = tf.constant[Int](0)
    val one = tf.constant[Int](1)
    val cell = new RNNCell[
        Output[T],
        (EncodedSequences[T], Output[Int], Seq[MultiHeadAttentionCache[Float]], Seq[MultiHeadAttentionCache[Float]]),
        Shape,
        ((Shape, Shape), Shape, Seq[(Shape, Shape)], Seq[(Shape, Shape)])] {
      override def outputShape: Shape = {
        Shape(numUnits)
      }

      override def stateShape: ((Shape, Shape), Shape, Seq[(Shape, Shape)], Seq[(Shape, Shape)]) = {
        ((Shape(-1, numUnits), Shape()),
            Shape(),
            (0 until numLayers).map(_ =>
              (Shape(attentionNumHeads, -1, attentionKeysDepth / attentionNumHeads),
                  Shape(attentionNumHeads, -1, attentionValuesDepth / attentionNumHeads))),
            (0 until numLayers).map(_ =>
              (Shape(attentionNumHeads, -1, attentionKeysDepth / attentionNumHeads),
                  Shape(attentionNumHeads, -1, attentionValuesDepth / attentionNumHeads))))
      }

      override def forward(
          input: RNNTuple[Output[T], (EncodedSequences[T], Output[Int], Seq[MultiHeadAttentionCache[Float]], Seq[MultiHeadAttentionCache[Float]])]
      ): RNNTuple[Output[T], (EncodedSequences[T], Output[Int], Seq[MultiHeadAttentionCache[Float]], Seq[MultiHeadAttentionCache[Float]])] = {
        val encodedSequences = input.state._1
        val encodedSequencesMaxLength = tf.shape(encodedSequences.states).slice(1)
        val inputPadding = tf.logicalNot(
          tf.sequenceMask(encodedSequences.lengths, encodedSequencesMaxLength, name = "Padding")).toFloat
        val encoderDecoderAttentionBias = Attention.attentionBiasIgnorePadding(inputPadding)

        // This is necessary in order to deal with the beam search tiling.
        val step = input.state._2(0)
        val currentStepPositionEmbeddings = positionEmbeddings(0).gather(step).expandDims(0).expandDims(1)
        var currentStepInput = input.output.expandDims(1) + currentStepPositionEmbeddings

        // Project the decoder input sequences, if necessary.
        val wordEmbeddingsSize = context.parameterManager.wordEmbeddingsType.embeddingsSize
        if (wordEmbeddingsSize != numUnits) {
          val projectionWeights = context.parameterManager.get[T](
            "ProjectionToDecoderNumUnits", Shape(wordEmbeddingsSize, numUnits))
          currentStepInput = Common.matrixMultiply(currentStepInput, projectionWeights)
        }

        val selfAttentionBias = tf.slice(
          decoderSelfAttentionBias(0, 0, ::, ::).gather(step),
          Seq(zero),
          Seq(step + one))
        val (output, updatedMultiHeadAttentionCache) = decode(
          encoderOutput = input.state._1.states.toFloat,
          decoderInputSequences = currentStepInput,
          decoderSelfAttentionBias = selfAttentionBias,
          decoderSelfAttentionCache = Some(input.state._3),
          encoderDecoderAttentionBias = encoderDecoderAttentionBias,
          encoderDecoderAttentionCache = Some(input.state._4))
        val currentStepOutput = output.squeeze(Seq(1))
        RNNTuple(
          output = currentStepOutput,
          state = (input.state._1, input.state._2 + one, updatedMultiHeadAttentionCache.map(_.get), input.state._4))
      }
    }

    // Create some constants that will be used during decoding.
    val tgtBosID = tf.constant[Int](Vocabulary.BEGIN_OF_SEQUENCE_TOKEN_ID)
    val tgtEosID = tf.constant[Int](Vocabulary.END_OF_SEQUENCE_TOKEN_ID)

    // Initialize the cache and the decoder RNN state.
    val embeddings = (ids: Output[Int]) => this.embeddings(ids)
    val batchSize = tf.shape(encodedSequences.lengths).slice(0)
    val decoderSelfAttentionCache = (0 until numLayers).map(_ => {
      MultiHeadAttentionCache(
        keys = Attention.splitHeads(
          tf.zeros[Float](tf.stack[Int](Seq(batchSize, zero, attentionKeysDepth))), attentionNumHeads),
        values = Attention.splitHeads(
          tf.zeros[Float](tf.stack[Int](Seq(batchSize, zero, attentionValuesDepth))), attentionNumHeads))
    })
    val initialState = (
        encodedSequences.copy(states = encoderOutput),
        tf.zeros[Int](batchSize.expandDims(0)),
        decoderSelfAttentionCache,
        encoderDecoderAttentionCache)

    // Create the decoder RNN.
    val output = {
      if (context.inferenceConfig.beamWidth > 1) {
        val decoder = BeamSearchDecoder(
          cell, initialState, embeddings, tf.fill[Int, Int](batchSize.expandDims(0))(tgtBosID),
          tgtEosID, context.inferenceConfig.beamWidth, context.inferenceConfig.lengthPenalty,
          outputLayer(numUnits))
        val tuple = decoder.decode(
          outputTimeMajor = false,
          maximumIterations = maxDecodingLength,
          parallelIterations = context.env.parallelIterations,
          swapMemory = context.env.swapMemory)
        Sequences(tuple._1.predictedIDs(---, 0), tuple._3(---, 0).toInt)
      } else {
        val decHelper = BasicDecoder.GreedyEmbeddingHelper[T, (EncodedSequences[T], Output[Int], Seq[MultiHeadAttentionCache[Float]], Seq[MultiHeadAttentionCache[Float]])](
          embeddingFn = embeddings,
          beginTokens = tf.fill[Int, Int](batchSize.expandDims(0))(tgtBosID),
          endToken = tgtEosID)
        val decoder = BasicDecoder(cell, initialState, decHelper, outputLayer(numUnits))
        val tuple = decoder.decode(
          outputTimeMajor = false,
          maximumIterations = maxDecodingLength,
          parallelIterations = context.env.parallelIterations,
          swapMemory = context.env.swapMemory)
        Sequences(tuple._1.sample, tuple._3)
      }
    }

    Sequences(output.sequences(---, 0 :: -1), output.lengths - 1)
  }

  protected def embeddings(
      ids: Output[Int]
  )(implicit context: ModelConstructionContext): Output[T] = {
    context.parameterManager.wordEmbeddings(context.tgtLanguageID, ids).castTo[T]
  }

  protected def decode(
      encoderOutput: Output[Float],
      decoderInputSequences: Output[T],
      decoderSelfAttentionBias: Output[Float],
      decoderSelfAttentionCache: Option[Seq[MultiHeadAttentionCache[Float]]],
      encoderDecoderAttentionBias: Output[Float],
      encoderDecoderAttentionCache: Option[Seq[MultiHeadAttentionCache[Float]]]
  )(implicit context: ModelConstructionContext): (Output[T], Seq[Option[MultiHeadAttentionCache[Float]]]) = {
    // Add the multi-head attention and the feed-forward layers.
    var x = decoderInputSequences.toFloat
    var y = x
    val updatedDecoderSelfAttentionCache = (0 until numLayers).map(layer => {
      tf.variableScope(s"Layer$layer") {
        val updatedDecoderSelfAttentionCache = tf.variableScope("SelfAttention") {
          val queryAntecedent = LayerProcessor.layerPreprocess(x, layerPreprocessors)
          val (output, updatedDecoderSelfAttentionCache) = Attention.multiHeadAttention(
            queryAntecedent = queryAntecedent,
            memoryAntecedent = None,
            bias = decoderSelfAttentionBias,
            totalKeysDepth = attentionKeysDepth,
            totalValuesDepth = attentionValuesDepth,
            outputsDepth = numUnits,
            numHeads = attentionNumHeads,
            attention = selfAttention,
            cache = decoderSelfAttentionCache.map(_ (layer)),
            name = "MultiHeadAttention")
          y = output
          if (layer == 0 && removeFirstLayerResidualConnection)
            x = LayerProcessor.layerPostprocess(x, y, layerPostprocessors.filter(_ != AddResidualConnection))
          else
            x = LayerProcessor.layerPostprocess(x, y, layerPostprocessors)
          updatedDecoderSelfAttentionCache
        }
        tf.variableScope("EncoderDecoderAttention") {
          val queryAntecedent = LayerProcessor.layerPreprocess(x, layerPreprocessors)
          y = Attention.multiHeadAttention(
            queryAntecedent = queryAntecedent,
            memoryAntecedent = Some(encoderOutput),
            bias = encoderDecoderAttentionBias,
            totalKeysDepth = attentionKeysDepth,
            totalValuesDepth = attentionValuesDepth,
            outputsDepth = numUnits,
            numHeads = attentionNumHeads,
            attention = selfAttention,
            cache = encoderDecoderAttentionCache.map(_ (layer)),
            name = "MultiHeadAttention")._1
          x = LayerProcessor.layerPostprocess(x, y, layerPostprocessors)
        }
        tf.variableScope("FeedForward") {
          val xx = LayerProcessor.layerPreprocess(x, layerPreprocessors)
          // TODO: What about the pad remover?
          y = feedForwardLayer(xx, None)
          x = LayerProcessor.layerPostprocess(x, y, layerPostprocessors)
        }
        updatedDecoderSelfAttentionCache
      }
    })

    // If normalization is done during layer preprocessing, then it should also be done on the output, since the
    // output can grow very large, being the sum of a whole stack of unnormalized layer outputs.
    (LayerProcessor.layerPreprocess(x, layerPreprocessors).castTo[T], updatedDecoderSelfAttentionCache)
  }
}
