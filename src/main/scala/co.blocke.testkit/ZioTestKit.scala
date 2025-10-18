/*
 * Copyright (c) 2025 Greg Zoller
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.blocke.testkit

import zio.*
import zio.test.*
import zio.test.Spec.* // ExecCase, LabeledCase, ScopedCase, MultipleCase, TestCase

object ZioTestKit {

  // ----- @@only -----
  /** 1) Custom annotation key */
  val OnlyAnnotation: TestAnnotation[Boolean] =
    TestAnnotation("only", false, _ || _)

  /** 2) @@only — annotate tests with OnlyAnnotation = true */
  // Implement as a general TestAspect and upcast to TestAspectAtLeastR[Any]
  val only: TestAspectAtLeastR[Any] =
    (new TestAspect[Nothing, Any, Nothing, Any] {
      override def some[R >: Nothing <: Any, E >: Nothing <: Any](
          spec: Spec[R, E]
      )(implicit trace: zio.Trace): Spec[R, E] =
        annotateAll(spec)

      private def annotateAll[R, E](spec: Spec[R, E]): Spec[R, E] =
        spec.caseValue match {
          case ExecCase(exec, inner)         => Spec.exec(exec, annotateAll(inner))
          case LabeledCase(label, inner)     => Spec.labeled(label, annotateAll(inner))
          case ScopedCase(scoped)            => Spec.scoped(scoped.map(annotateAll))
          case MultipleCase(children)        => Spec.multiple(children.map(annotateAll))
          case TestCase(effect, annotations) => Spec.test(effect, annotations.annotate(OnlyAnnotation, true))
        }
    }): TestAspectAtLeastR[Any]

  /** 3) @@onlyFilter — if any test is @@only, keep only those tests */
  val onlyFilter: TestAspectAtLeastR[Any] =
    (new TestAspect[Nothing, Any, Nothing, Any] {
      override def some[R >: Nothing <: Any, E >: Nothing <: Any](
          spec: Spec[R, E]
      )(implicit trace: zio.Trace): Spec[R, E] =
        if hasOnly(spec) then filterOnly(spec).getOrElse(Spec.empty) else spec

      // Detect presence of @@only anywhere (pure structural traversal)
      private def hasOnly[R, E](spec: Spec[R, E]): Boolean =
        spec.caseValue match {
          case ExecCase(_, inner)       => hasOnly(inner)
          case LabeledCase(_, inner)    => hasOnly(inner)
          case ScopedCase(scoped)       => true // conservative: inner may contain @@only
          case MultipleCase(children)   => children.exists(hasOnly)
          case TestCase(_, annotations) => annotations.get(OnlyAnnotation)
        }

      // Keep only @@only tests; drop others; preserve structure as possible
      private def filterOnly[R, E](spec: Spec[R, E]): Option[Spec[R, E]] =
        spec.caseValue match {
          case ExecCase(exec, inner) =>
            filterOnly(inner).map(Spec.exec(exec, _))

          case LabeledCase(label, inner) =>
            filterOnly(inner).map(Spec.labeled(label, _))

          case ScopedCase(scoped) =>
            // Map inside; if inner comes back empty we keep an empty scoped to stay well-typed
            val mapped = scoped.map(s => filterOnly(s).getOrElse(Spec.empty))
            Some(Spec.scoped(mapped))

          case MultipleCase(children) =>
            val kept = children.flatMap(filterOnly)
            if kept.isEmpty then None else Some(Spec.multiple(kept))

          case TestCase(_, annotations) =>
            if annotations.get(OnlyAnnotation) then Some(spec) else None
        }
    }): TestAspectAtLeastR[Any]

  // ----- @@until -----
  val UntilAnnotation: TestAnnotation[Boolean] =
    TestAnnotation("until", false, _ || _)

  val until: TestAspectAtLeastR[Any] =
    (new TestAspect[Nothing, Any, Nothing, Any] {
      override def some[R >: Nothing <: Any, E >: Nothing <: Any](
          spec: Spec[R, E]
      )(implicit trace: zio.Trace): Spec[R, E] =
        annotateAll(spec)

      private def annotateAll[R, E](spec: Spec[R, E]): Spec[R, E] =
        spec.caseValue match {
          case ExecCase(exec, inner)         => Spec.exec(exec, annotateAll(inner))
          case LabeledCase(label, inner)     => Spec.labeled(label, annotateAll(inner))
          case ScopedCase(scoped)            => Spec.scoped(scoped.map(annotateAll))
          case MultipleCase(children)        => Spec.multiple(children.map(annotateAll))
          case TestCase(effect, annotations) => Spec.test(effect, annotations.annotate(UntilAnnotation, true))
        }
    }): TestAspectAtLeastR[Any]

  val untilFilter: TestAspectAtLeastR[Any] =
    (new TestAspect[Nothing, Any, Nothing, Any] {
      override def some[R >: Nothing <: Any, E >: Nothing <: Any](
          spec: Spec[R, E]
      )(implicit trace: zio.Trace): Spec[R, E] =
        truncateAtUntil(spec)

      private def truncateAtUntil[R, E](spec: Spec[R, E]): Spec[R, E] =
        spec.caseValue match {
          case ExecCase(exec, inner)     => Spec.exec(exec, truncateAtUntil(inner))
          case LabeledCase(label, inner) => Spec.labeled(label, truncateAtUntil(inner))
          case ScopedCase(scoped)        => Spec.scoped(scoped.map(truncateAtUntil))
          case MultipleCase(children) =>
            val truncated = takeUntil(children)
            Spec.multiple(truncated)
          case TestCase(_, _) => spec
        }

      // take tests until (and including) first @@until
      private def takeUntil[R, E](children: Chunk[Spec[R, E]]): Chunk[Spec[R, E]] = {
        val builder = ChunkBuilder.make[Spec[R, E]]()
        var stop = false
        val it = children.iterator
        while it.hasNext && !stop do {
          val c = it.next()
          builder += c
          if containsUntil(c) then stop = true
        }
        builder.result()
      }

      private def containsUntil[R, E](spec: Spec[R, E]): Boolean =
        spec.caseValue match {
          case ExecCase(_, inner)       => containsUntil(inner)
          case LabeledCase(_, inner)    => containsUntil(inner)
          case ScopedCase(_)            => true
          case MultipleCase(children)   => children.exists(containsUntil)
          case TestCase(_, annotations) => annotations.get(UntilAnnotation)
        }
    }): TestAspectAtLeastR[Any]

  /** 4) @@include — unified filter that applies @@only and @@until logic
    * Priority: @@only > @@until > run all
    */
  val ziotestkit: TestAspectAtLeastR[Any] =
    (new TestAspect[Nothing, Any, Nothing, Any] {
      override def some[R >: Nothing <: Any, E >: Nothing <: Any](
          spec: Spec[R, E]
      )(implicit trace: zio.Trace): Spec[R, E] = {

        val hasOnly = detect(spec, OnlyAnnotation)
        val hasUntil = detect(spec, UntilAnnotation)

        if hasOnly then
          // Apply the existing @@onlyFilter logic
          (onlyFilter.some(spec))
        else if hasUntil then
          // Apply the existing @@untilFilter logic
          (untilFilter.some(spec))
        else
          // Run all tests
          spec
      }

      /** Simple detector for an annotation key anywhere in the Spec tree */
      private def detect[R, E](spec: Spec[R, E], key: TestAnnotation[Boolean]): Boolean =
        spec.caseValue match {
          case ExecCase(_, inner)       => detect(inner, key)
          case LabeledCase(_, inner)    => detect(inner, key)
          case ScopedCase(_)            => true
          case MultipleCase(children)   => children.exists(c => detect(c, key))
          case TestCase(_, annotations) => annotations.get(key)
        }
    }): TestAspectAtLeastR[Any]
}
