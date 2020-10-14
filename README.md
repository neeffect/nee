[![Codacy Badge](https://api.codacy.com/project/badge/Grade/b7bc721d1d92494b90f5346b33dc398c)](https://www.codacy.com/manual/jarekratajski/nee?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=neeffect/nee&amp;utm_campaign=Badge_Grade)

# NEE

Not so enterprisy effects.

Status: *Work in Progress*.

Help needed. If you want to contribute read [this](contributing.md)

## Goal

Provide kotlin friendly extensible effects using functional approach.

It should enable (more or less) features known from aspect oriented frameworks, 
but in a clean, non magic way. 

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
    private val  f =  {jdbcConnection:Connection ->
            {p: P ->
            //code
        }
    }
    val enterprisyF = Nee.pure(
        secure
        .and(retryable)
        .and(cacheable)
        .and(transactional), f)
    //declaration above means security is checked before retrial
    //and retrial is made before cache which happens before transaction 
 }
```

motto :  
<https://twitter.com/mdiep/status/1187088700724989952>

## Core concept

### Business function
```kotlin
businessFunction  = (R) -> (P) -> A 
```

Where:
 **R**  - is an environment / infrastructure that a function may use
 **P** - is a *tracked* parameter to function (optional)
 **A** - is a result of the function

This is very generic way to present any piece of work.

In order to use NEE logic must be presented in this form ( which is mostly simple).

(we will show it later)
(let me be honest, the only reason this crazy param P is used is the possibility to define `caching`)

### Putting inside Nee Monad

Next step is to put business function inside Nee monad.
Nee monad wraps business logic with a given infrastructure.

```kotlin
val functionOnRealHardware = Nee.pure(Nop)(businessFunction)
```

Now `functionOnRealHardware` is blessed with side effects and now is 
wrapped inside Nee monad. It is enclosed in a monad to make it "composable"
with other functions. Just think of performing multiple jdbc calls inside one transaction. 

As for side effects we see `Nop`... meaning not a real one - but it is time to tell more about `Effects`

### Effects

 Effect is a special class that tells how to connect businessFunction with a reality.
 
 ```kotlin
 interface Effect<R, E> {
     fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Out<E, A>, R>
  }
```

In order to provide effect we need to implement interface as above.
Where:
-   **R** as before is some environment object, think this is how to get DB connection from,

-   **P** is a generic parameter that might be used by effect (actually it is only needed for caching)

-   **E** is an error that might happen during application of effect 
            (notice - it does not have to be Exception)
            
```Out``` is special object that represents the final result of calculation. 
Think of it as:  `Out<E,T>  =~= Future<Either<E,T>>`

An effect:
takes a function (businessFunction) which may rely on environment `R`, and on a  parameter `P`, 
giving  some result `A`. 
Then wraps it into a function that:
    takes environment `R` (no change),
      - runs some infrastructure code (effect),  
      - later takes `P` and returns `Out` object (the result)
      - it also returns  changed environment `(R)` - think that maybe transaction is now started            

*Notice  - this no a typical effect as known from haskell 
more a Side Effect or simply maybe it should be called Aspect, 
cause it tries to mimic runtime aspects.

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
 val f1 = Nee.constP(jdbcTransaction) {connection ->
            connection.prepareStatement()
            [F1 code]
    }

 val f2 = Nee.constP(jdbcTransaction) {connection ->
            connection.prepareStatement()
            [F2 code]
    }

//we join results but with separate transactions
val f = f1.perform(jdbcConfig).flatMap { f2.perform(jdbcConfig)} 
```

## TODO
- Code:
  - remove warnings
  - naming & long lambdas clean 
- Ideas:
  - R extract (for effect) - multiple db support
  - R as Map (ugly but practical)
  - arrow?
  - Swap P, E in  -> NEE R,P,E,A
- Tests:
  - real assertions
  - unhappy paths
  - load tests (sanity)
