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

package org.platanios.symphony.mt.experiments.config

import org.platanios.symphony.mt.data.{DataConfig, GeneratedVocabulary, MergedVocabularies, NoVocabulary}
import org.platanios.symphony.mt.data.processors._
import org.platanios.symphony.mt.vocabulary.{BPEVocabularyGenerator, SimpleVocabularyGenerator}

import com.typesafe.config.Config

import java.nio.file.Paths

/**
  * @author Emmanouil Antonios Platanios
  */
object DataConfigConfigParser extends ConfigParser[DataConfig] {
  override def parse(config: Config): DataConfig = {
    val tokenizer = config.getString("tokenizer")
    val cleaner = config.getString("cleaner")
    val vocabularyType = config.getString("vocabulary.type")
    DataConfig(
      dataDir = Paths.get(config.getString("data-dir")),
      loaderBufferSize = config.getInt("loader-buffer-size"),
      tokenizer = tokenizer.split(":") match {
        case Array(name) if name == "none" => NoTokenizer
        case Array(name) if name == "moses" => MosesTokenizer()
        case Array(name) if name == "mteval13" => MTEval13Tokenizer(preserveCase = true)
        case _ => throw new IllegalArgumentException(s"'$tokenizer' does not represent a valid tokenizer.")
      },
      cleaner = cleaner.split(":") match {
        case Array(name) if name == "none" => NoCleaner
        case Array(name) if name == "moses" => MosesCleaner()
        case _ => throw new IllegalArgumentException(s"'$cleaner' does not represent a valid cleaner.")
      },
      vocabulary = vocabularyType match {
        case "none" => NoVocabulary
        case "merged" => MergedVocabularies
        case "word-count" =>
          val shared = config.getBoolean("vocabulary.shared")
          val size = config.getInt("vocabulary.size")
          val minCount = if (config.hasPath("vocabulary.min-count")) config.getInt("vocabulary.min-count") else -1
          GeneratedVocabulary(
            generator = SimpleVocabularyGenerator(size, minCount),
            shared = shared)
        case "bpe" =>
          val shared = config.getBoolean("vocabulary.shared")
          val caseSensitive = config.getBoolean("vocabulary.case-sensitive")
          val numMergeOps = config.getInt("vocabulary.num-merge-ops")
          val minCount = if (config.hasPath("vocabulary.min-count")) config.getInt("vocabulary.min-count") else -1
          GeneratedVocabulary(
            generator = BPEVocabularyGenerator(numMergeOps, caseSensitive = caseSensitive, countThreshold = minCount),
            shared = shared)
        case _ => throw new IllegalArgumentException(s"'$vocabularyType' does not represent a valid vocabulary type.")
      },
      parallelPortion = config.getDouble("parallel-portion").toFloat,
      trainBatchSize = config.getInt("train-batch-size"),
      inferBatchSize = config.getInt("infer-batch-size"),
      evalBatchSize = config.getInt("eval-batch-size"),
      numBuckets = config.getInt("num-buckets"),
      srcMaxLength = config.getInt("src-max-length"),
      tgtMaxLength = config.getInt("tgt-max-length"),
      bufferSize = config.getInt("input-pipeline-buffer-size"),
      numShards = config.getInt("input-pipeline-num-shards"),
      shardIndex = config.getInt("input-pipeline-shard-index"),
      numParallelCalls = config.getInt("input-pipeline-num-parallel-calls"),
      unknownToken = config.getString("vocabulary.unknown-token"),
      beginOfSequenceToken = config.getString("vocabulary.begin-of-sequence-token"),
      endOfSequenceToken = config.getString("vocabulary.end-of-sequence-token"))
  }

  override def tag(config: Config, parsedValue: => DataConfig): Option[String] = {
    val stringBuilder = new StringBuilder()
    stringBuilder.append(s"${parsedValue.tokenizer}")
    stringBuilder.append(s".${parsedValue.cleaner}")
    stringBuilder.append(s".${parsedValue.vocabulary}")
    stringBuilder.append(s".pp:${(parsedValue.parallelPortion * 100).toInt}")
    stringBuilder.append(s".bs:${parsedValue.trainBatchSize}")
    stringBuilder.append(s".nb:${parsedValue.numBuckets}")
    stringBuilder.append(s".sml:${parsedValue.srcMaxLength}")
    stringBuilder.append(s".tml:${parsedValue.tgtMaxLength}")
    Some(stringBuilder.toString)
  }
}