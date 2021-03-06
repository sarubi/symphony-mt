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

package org.platanios.symphony.mt.data.loaders

import org.platanios.symphony.mt.Language
import org.platanios.symphony.mt.Language._
import org.platanios.symphony.mt.data._
import org.platanios.symphony.mt.data.processors.{FileProcessor, Normalizer}

import better.files._

import java.nio.file.Path

/**
  * @author Emmanouil Antonios Platanios
  */
class EuroparlV8Loader(
    override val srcLanguage: Language,
    override val tgtLanguage: Language,
    val config: DataConfig
) extends ParallelDatasetLoader(srcLanguage = srcLanguage, tgtLanguage = tgtLanguage) {
  require(
    EuroparlV8Loader.isLanguagePairSupported(srcLanguage, tgtLanguage),
    "The provided language pair is not supported by the Europarl v8 dataset.")

  override def name: String = "Europarl v8"

  override def dataConfig: DataConfig = {
    config.copy(dataDir =
        config.dataDir
            .resolve("europarl-v8")
            .resolve(s"${srcLanguage.abbreviation}-${tgtLanguage.abbreviation}"))
  }

  override def downloadsDir: Path = config.dataDir.resolve("europarl-v8").resolve("downloads")

  private[this] def reversed: Boolean = {
    EuroparlV8Loader.supportedLanguagePairs.contains((tgtLanguage, srcLanguage))
  }

  private[this] def corpusFilenamePrefix: String = {
    s"europarl-v8.${if (reversed) s"$tgt-$src" else s"$src-$tgt"}"
  }

  /** Sequence of files to download as part of this dataset. */
  override def filesToDownload: Seq[(String, String)] = {
    Seq((s"${EuroparlV8Loader.url}/${EuroparlV8Loader.archivePrefix}.tgz", s"${EuroparlV8Loader.archivePrefix}.tgz"))
  }

  /** Returns all the corpora (tuples containing tag, source file, target file, and a file processor to use)
    * of this dataset type. */
  override def corpora(datasetType: DatasetType): Seq[(ParallelDataset.Tag, File, File, FileProcessor)] = {
    datasetType match {
      case Train => Seq((EuroparlV8Loader.Train,
          File(downloadsDir) / EuroparlV8Loader.archivePrefix /
              EuroparlV8Loader.archivePrefix / s"$corpusFilenamePrefix.$src",
          File(downloadsDir) / EuroparlV8Loader.archivePrefix /
              EuroparlV8Loader.archivePrefix / s"$corpusFilenamePrefix.$tgt",
          Normalizer))
      case _ => Seq.empty
    }
  }
}

object EuroparlV8Loader {
  val url          : String = "http://data.statmt.org/wmt16/translation-task"
  val archivePrefix: String = "training-parallel-ep-v8"

  val supportedLanguagePairs: Set[(Language, Language)] = Set((Finnish, English), (Romanian, English))

  def isLanguagePairSupported(srcLanguage: Language, tgtLanguage: Language): Boolean = {
    supportedLanguagePairs.contains((srcLanguage, tgtLanguage)) ||
        supportedLanguagePairs.contains((tgtLanguage, srcLanguage))
  }

  def apply(
      srcLanguage: Language,
      tgtLanguage: Language,
      dataConfig: DataConfig
  ): EuroparlV8Loader = {
    new EuroparlV8Loader(srcLanguage, tgtLanguage, dataConfig)
  }

  case object Train extends ParallelDataset.Tag {
    override val value: String = "europarl-v8/train"
  }
}
