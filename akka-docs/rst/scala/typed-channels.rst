.. _typed-channels:

##############
Typed Channels
##############

Motivation
==========

Actors derive great strength from their strong encapsulation, which enables
internal restarts as well as changing behavior and also composition. The last
one is enabled by being able to inject an actor into a message exchange
transparently, because all either side ever sees is an :class:`ActorRef`. The
straight-forward way to implement this encapsulation is to keep the actor
references untyped, and before the advent of macros in Scala 2.10 this was the
only tractable way.

As a motivation for change consider the following simple example:

.. includecode:: code/docs/channels/ChannelDocSpec.scala#motivation0

.. includecode:: code/docs/channels/ChannelDocSpec.scala#motivation1

This is an error which is quite common, and the reason is that the compiler
does not catch it and cannot warn about it. Now if there were some type
restrictions on which messages the ``commandProcessor`` can process, that would
be a different story:

.. includecode:: code/docs/channels/ChannelDocSpec.scala#motivation2

The :class:`ChannelRef` wraps a normal untyped :class:`ActorRef`, but it
expresses a type constraint, namely that this channel accepts only messages of
type :class:`Request`, to which it may reply with messages of type
:class:`Reply`. The types do not express any guarantees on how many messages
will be exchanged, whether they will be received or processed, or whether a
reply will actually be sent. They only restrict those actions which are known
to be doomed already at compile time. In this case the second line would flag
an error, since the companion object ``Command`` is not an instance of type
:class:`Request`.

While this example looks pretty simple, the implications are profound. In order
to be useful, the system must be as reliable as you would expect a type system
to be. This means that unless you step outside of the it (i.e. doing the
equivalent of ``.asInstanceOf[_]``) you shall be protected, failures shall be
recognized and flagged. There are a number of challenges included in this
requirement, which are discussed in the following sections. If you are reading
this chapter for the first time and are not currently interested in exactly why
things are as they are, you may skip ahead to `Terminology`_.

The Type Pollution Problem
--------------------------

What if an actor accepts two different types of messages? It might be a main
communications channel which is forwarded to worker actors for performing some
long-running and/or dangerous task, plus an administrative channel for the
routing of requests. Or it might be a generic message throttler which accepts a
generic channel for passing it through (which delay where appropriate) and a
management channel for setting the throttling rate. In the second case it is
especially easy to see that those two channels will probably not be related,
their types will not be derived from a meaningful common supertype; instead the
least upper bound will probably be :class:`AnyRef`. If a typed channel
reference only had the capability to express a single type, this type would
then be no restriction anymore.

One solution to this is to never expose references describe more than one
channel at a time. But where would these references come from? It would be very
difficult to make this construction process type-safe, and it would also be an
inconvenient restriction, since message ordering guarantees only apply for the
same sender–receive pair, and if there are relations between the messages sent
on multiple channels those would need more boilerplate code to realize than if
all interaction were possible through a single reference.

The other solution thus is to express multiple channel types by a single
channel reference, which requires the implementation of type lists and
computations on these. And as we will see below it also requires the
specification of possibly multiple reply channels per input type, hence a type
map. The implementation chosen uses type lists like this:

.. includecode:: code/docs/channels/ChannelDocSpec.scala#motivation-types

This type expresses two channels: type ``A`` may stimulate replies of type
``B``, while type ``C`` may evoke replies of type ``D``. The type operator
``:+:`` is a binary type which form a list of these channel definitions, and
like every good list it ends with an empty terminator ``TNil``.

The Reply Problem
-----------------

Akka actors have the power to reply to any message they receive, which is also
a message send and shall also be covered by typed channels. Since the sending
actor is the one which will also receive the reply, this needs to be verified.
The solution to this problem is that in addition to the ``self`` reference,
which is implicitly picked up as the sender for untyped actor interactions,
there is also a ``selfChannel`` which describes the typed channels handled by
this actor. Thus at the call site of the message send it must be verified that
this actor can actually handle the reply for that given message send.

The Sender Ping-Pong Problem
----------------------------

After successfully sending a message to an actor over a typed channel, that
actor will have a reference to the message’s sender, because normal Akka
message processing rules apply. For this sender reference there must exist a
typed channel reference which describes the possible reply types which are
applicable for each of the incoming message channels. We will see below how
this reference is provided in the code, the problem we want to highlight here
is a different one: the nature of any sender reference is that it is highly
dynamic, the compiler cannot possibly know who sent the message we are
currently processing.

But this does not mean that all hope is lost: the solution is to do *all*
type-checking at the call site of the message send. The receiving actor just
needs to declare its channel descriptions in its own type, and channel
references are derived at construction from this type (implying the existence
of a typed ``actorOf``). Then the actor knows for each received message type
which the allowed reply types are. The typed channel for the sender reference
hence has the reply types for the current input channel as its own input types,
but what should the reply types be? This is the ping-pong problem:

* ActorA sends MsgA to ActorB

* ActorB replies with MsgB

* ActorA replies with MsgC

Every “reply” uses the sender channel, which is dynamic and hence only known
partially. But ActorB did not know who sent the message it just replied to and
hence it cannot check that it can process the possible replies following that
message send. Only ActorA could have known, because it knows its own channels
as well as ActorB’s channels completely. The solution is thus to recursively
verify the message send, following all reply channels until all possible
message types to be sent have been verified. This sounds horribly complex, but
the algorithm for doing so actually has a worst-case complexity of O(N) where N
is the number of input channels of ActorA or ActorB, whoever has fewer.

The Parent Problem
------------------

There is one other actor reference which is available to ever actor: its
parent. Since the child–parent relationship is established permanently when the
child is created by the parent, this problem is easily solvable by encoding the
requirements of the child for its parent channel in its type signature having
the typed variant of ``actorOf`` verify this against the ``selfChannel``.

Anecdotally, since the guardian actor does not care at all about message sent
to it, top-level type channel actors must declare their parent channel to be
empty.

The Exposure/Restriction Problem
--------------------------------



The Forwarding Problem
----------------------



The JVM Erasure Problem
-----------------------



The Actor Lookup Problem
------------------------



Terminology
===========



Declaring an Actor with Channels
================================



Implementation Restrictions
---------------------------

erasure-based dispatch
typeTag needed

Sending Messages across Channels
================================

The Rules
---------

Operations on typed channels are composable and obey a few simple rules:

* the message to be sent can be one of three things:

  * a :class:`Future[_]`, in which case the contained value will be sent once
    available; the value will be unwrapped if it is a :class:`WrappedMessage[_, _]`

  * a :class:`WrappedMessage[_, _]`, which will be unwrapped (i.e. only the
    value is sent)

  * everything else is sent as is

* the operators are fully symmetric, i.e. ``-!->`` and ``<-!-`` do the same
  thing provided the arguments also switch places

* sending with ``-?->`` or ``<-?-`` always returns a
  ``Future[WrappedMessage[_, _]]`` representing all possible reply channels,
  even if there is only one (use ``.lub`` to get a :class:`Future[_]` with the
  most precise single type for the value)

* sending a :class:`Future[_]` with ``-!->`` or ``<-!-`` returns a new
  :class:`Future[_]` which will be completed with the value after it has been
  sent; sending a strict value returns that value

Reply Verification
------------------



Asking Channels
===============



Forwarding Polymorphic Channels
-------------------------------



How to read The Types
=====================


