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

package org.platanios.symphony.mt.models.attention

import org.platanios.tensorflow.api._
import org.platanios.tensorflow.api.learn.Mode
import org.platanios.tensorflow.api.learn.layers.rnn.cell.RNNCell
import org.platanios.tensorflow.api.ops.control_flow.WhileLoopVariable
import org.platanios.tensorflow.api.ops.rnn.attention.{AttentionWrapperCell, AttentionWrapperState}
import org.platanios.tensorflow.api.ops.variables.{ConstantInitializer, ZerosInitializer}

/**
  * @author Emmanouil Antonios Platanios
  */
case class BahdanauAttention(
    normalized: Boolean = false,
    probabilityFn: (Output) => Output = tf.softmax(_, name = "Probability"),
    scoreMask: Float = Float.NegativeInfinity
) extends Attention {
  override def create[S, SS](
      cell: RNNCell[Output, Shape, S, SS],
      memory: Output,
      memorySequenceLengths: Output,
      numUnits: Int,
      inputSequencesLastAxisSize: Int,
      initialState: S,
      useAttentionLayer: Boolean,
      outputAttention: Boolean,
      mode: Mode
  )(implicit
      evS: WhileLoopVariable.Aux[S, SS],
      evSDropout: ops.rnn.cell.DropoutWrapper.Supported[S]
  ): (AttentionWrapperCell[S, SS], AttentionWrapperState[S, SS]) = tf.createWithVariableScope("BahdanauAttention") {
    val memoryWeights = tf.variable("MemoryWeights", memory.dataType, Shape(numUnits, numUnits), null)
    val queryWeights = tf.variable("QueryWeights", memory.dataType, Shape(numUnits, numUnits), null)
    val scoreWeights = tf.variable("ScoreWeights", memory.dataType, Shape(numUnits), null)
    val (normFactor, normBias) = {
      if (normalized) {
        (tf.variable("Factor", memory.dataType, Shape.scalar(), ConstantInitializer(Math.sqrt(1.0f / numUnits))).value,
            tf.variable("Bias", memory.dataType, Shape(numUnits), ZerosInitializer).value)
      } else {
        (null, null)
      }
    }
    val attention = tf.BahdanauAttention(
      memory, memoryWeights.value, queryWeights.value, scoreWeights.value, memorySequenceLengths, normFactor, normBias,
      probabilityFn, scoreMask, "Attention")
    val attentionWeights = {
      if (useAttentionLayer)
        Seq(tf.variable(
          "AttentionWeights", attention.dataType, Shape(numUnits + memory.shape(-1), numUnits), null).value)
      else
        null
    }
    val createdCell = cell.createCell(mode, Shape(inputSequencesLastAxisSize + numUnits))
    val attentionCell = tf.AttentionWrapperCell(
      createdCell, Seq(attention), attentionWeights, outputAttention = outputAttention)
    (attentionCell, attentionCell.initialState(initialState, memory.dataType))
  }
}
