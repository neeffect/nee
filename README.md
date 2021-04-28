[![Codacy Badge](https://app.codacy.com/project/badge/Grade/3613db6c1833407d9daa325d110b81ad)](https://www.codacy.com/gh/neeffect/nee/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=neeffect/nee&amp;utm_campaign=Badge_Grade)

# NEE

Not so enterprisy effects.

Status: *Work in Progress*.

Help needed. If you want to contribute read [this](contributing.md)

## Goal

Provide kotlin friendly extensible effects using functional approach.

It should enable (more or less) features known from aspect oriented frameworks, but in a clean, non magic way.

Instead of writing:

``` kotlin
class Hasiok {
    @Resource
    val jdbcConnection: Connection

    @Transactional
    @Secure
    @Cacheable
    @Retryable
    fun f(p:P) {
        //code
    }
}
```

It is possible to have similar goodies rewriting code like below:

```kotlin
class  Hasiok {

    fun enterprisyFunction(x:Int) = Nee.pure(
        secure + retryable + cache.of(x) + transactional
        )  {jdbcConnection:Connection ->
                    //code using jdbcConnection
    }
    //declaration above means security is checked before retrial
    //and retrial is made before cache which happens before transaction 
 }
```

motto :  
<https://twitter.com/mdiep/status/1187088700724989952>

## Core concept

### Business function

```kotlin
businessFunction  = (R) -> A 
```

Where:
**R**  - is an environment / infrastructure that a function may use
**A** - is a result of the function

Typically R would be something like a `database connection`. It might be `security context`. It can be all infrasture
handlers that are relevant in a given context.

### Putting inside Nee Monad

Next step is to put business function inside Nee monad. Nee monad wraps business logic with a given infrastructure.

```kotlin
val functionOnRealHardware = Nee.pure(noEffect())(businessFunction)
```

Now `functionOnRealHardware` is blessed with side effects and now is wrapped inside Nee monad. It is enclosed in a monad
to make it "composable"
with other functions. Just think of performing multiple jdbc calls inside one transaction.

As for side effects we see `noEffect()`... meaning not a real one - but it is time to tell more about `Effects`

### Effects

Effect is a special class that tells how to wrap businessFunction and run it providing infrastructure.

 ```kotlin
 interface Effect<R, E> {
     fun <A, P> wrap(f: (R) -> A): (R) -> Pair<Out<E, A>, R>
  }
```

In order to provide effect we need to implement interface as above. Where:

- **R** as before is some environment object, think this is how to get DB connection from,

- **E** is an error that might happen during application of effect
  (notice - it does not have to be Exception)

```Out``` is special object that represents the final result of calculation. Think of it
as:  `Out<E,T>  =~= Future<Either<E,T>>`

An effect:
takes a function (businessFunction) which may rely on environment `R`, and on a parameter `P`, giving some result `A`.
Then wraps it into a function that:
takes environment `R` (no change), - runs some infrastructure code (effect), - it also returns changed environment `(R)`
- think that maybe transaction is now started

### Monads

`Nee` is in fact a monad. This means that it is possible to chain various business functions executions.

`Out` is also a monad. This means it is possible to chain results.

#### Explanation

If you want both methods to run inside same transaction:

```kotlin
 val f1 = Nee.constP(jdbcTransaction) {connection ->
            connection.prepareStatement()
            [F1 code]
    }

 val f2 = Nee.constP(jdbcTransaction) {connection ->
            connection.prepareStatement()
            [F2 code]
    }

// f has both logic of f1 and f2 enclosed in a single transaction
val f = f1.flatMap { f2 }.perform(jdbcConfig)
```

if you want to run in separate transactions:

```kotlin
 val f1 = Nee.with(jdbcTransaction) {connection ->
            connection.prepareStatement()
            [F1 code]
    }

 val f2 = Nee.with(jdbcTransaction) {connection ->
            connection.prepareStatement()
            [F2 code]
    }

//we join results but with separate transactions
val f = f1.perform(jdbcConfig).flatMap { f2.perform(jdbcConfig)} 
```

## TODO

- Code:

    - naming & long lambdas clean
- Ideas:
    - R extract (for effect) - multiple db support

    - arrow?
- Tests:
    - real assertions
    - unhappy paths
    - load tests (sanity)
