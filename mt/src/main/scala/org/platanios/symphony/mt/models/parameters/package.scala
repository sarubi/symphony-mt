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

package org.platanios.symphony.mt.models

import org.platanios.symphony.mt.Language
import org.platanios.symphony.mt.config.TrainingConfig

/**
  * @author Emmanouil Antonios Platanios
  */
package object parameters {
  /** Determines all language index pairs that are relevant given the current model configuration. */
  def languageIndexPairs(
      languages: Seq[Language],
      trainingConfig: TrainingConfig
  ): Seq[(Int, Int)] = {
    if (trainingConfig.languagePairs.nonEmpty) {
      trainingConfig.languagePairs.toSeq.map(pair => {
        (languages.indexOf(pair._1), languages.indexOf(pair._2))
      })
    } else {
      languages.indices
          .combinations(2)
          .map(c => (c(0), c(1)))
          .flatMap(p => Seq(p, (p._2, p._1)))
          .toSeq
    }
  }
}
