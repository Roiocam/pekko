/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata

object GSet {
  private val _empty: GSet[Any] = new GSet(Set.empty)(None)
  def empty[A]: GSet[A] = _empty.asInstanceOf[GSet[A]]
  def apply(): GSet[Any] = _empty
  private[pekko] def apply[A](set: Set[A]): GSet[A] = new GSet(set)(None)

  /**
   * Java API
   */
  def create[A](): GSet[A] = empty[A]

  // unapply from case class
}

/**
 * Implements a 'Add Set' CRDT, also called a 'G-Set'. You can't
 * remove elements of a G-Set.
 *
 * It is described in the paper
 * <a href="https://inria.hal.science/file/index/docid/555588/filename/techreport.pdf">A comprehensive study of Convergent and Commutative Replicated Data Types</a>.
 *
 * A G-Set doesn't accumulate any garbage apart from the elements themselves.
 *
 * This class is immutable, i.e. "modifying" methods return a new instance.
 */
@SerialVersionUID(1L)
final case class GSet[A] private (elements: Set[A])(override val delta: Option[GSet[A]])
    extends DeltaReplicatedData
    with ReplicatedDelta
    with ReplicatedDataSerialization
    with FastMerge {

  type T = GSet[A]
  type D = GSet[A]

  /**
   * Java API
   */
  def getElements(): java.util.Set[A] = {
    import org.apache.pekko.util.ccompat.JavaConverters._
    elements.asJava
  }

  def contains(a: A): Boolean = elements(a)

  def isEmpty: Boolean = elements.isEmpty

  def size: Int = elements.size

  /**
   * Adds an element to the set
   */
  def +(element: A): GSet[A] = add(element)

  /**
   * Adds an element to the set
   */
  def add(element: A): GSet[A] = {
    val newDelta = delta match {
      case Some(e) => Some(new GSet(e.elements + element)(None))
      case None    => Some(new GSet[A](Set.apply[A](element))(None))
    }
    assignAncestor(new GSet[A](elements + element)(newDelta))
  }

  override def merge(that: GSet[A]): GSet[A] =
    if ((this eq that) || that.isAncestorOf(this)) this.clearAncestor()
    else if (this.isAncestorOf(that)) that.clearAncestor()
    else {
      clearAncestor()
      new GSet[A](elements.union(that.elements))(None)
    }

  override def mergeDelta(thatDelta: GSet[A]): GSet[A] = merge(thatDelta)

  override def zero: GSet[A] = GSet.empty

  override def resetDelta: GSet[A] =
    if (delta.isEmpty) this
    else assignAncestor(new GSet[A](elements)(None))

  override def toString: String = s"G$elements"

  def copy(e: Set[A]) = new GSet[A](e)(delta)

  @deprecated("Kept for binary compatibility", since = "Akka 2.6.16")
  private[ddata] def copy$default$1(): Set[A] = elements
}

object GSetKey {
  def create[A](id: String): Key[GSet[A]] = GSetKey(id)
}

@SerialVersionUID(1L)
final case class GSetKey[A](_id: String) extends Key[GSet[A]](_id) with ReplicatedDataSerialization
