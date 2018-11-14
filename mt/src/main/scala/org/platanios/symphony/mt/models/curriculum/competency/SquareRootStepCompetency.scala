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

package org.platanios.symphony.mt.models.curriculum.competency

import org.platanios.tensorflow.api._

/**
  * @author Emmanouil Antonios Platanios
  */
class SquareRootStepCompetency[T: TF : IsNotQuantized](
    val initialValue: T,
    val numStepsToFullCompetency: T
) extends Competency[Output[T]] {
  override def currentLevel(step: Output[Long]): Output[T] = {
    val zero = tf.zeros[T](Shape())
    val one = tf.ones[T](Shape())
    val c0Square = tf.square(tf.constant[T](initialValue))
    val T = tf.constant[T](numStepsToFullCompetency)
    val t = step.castTo[T]
    tf.maximum(tf.minimum(tf.sqrt((t * (one - c0Square) / T) + c0Square), one), zero)
  }
}