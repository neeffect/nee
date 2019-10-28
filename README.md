# NEE

Not so enterprisy effects.

This is Work in Progress project.

Goal is to provide kotlin friendly extensible effects using functional approach.

It should enable (more or less) features known from aspect oriented frameworks, 
but in a clean  non magic way 


motto :  
https://twitter.com/mdiep/status/1187088700724989952




# Core concept

## Business function
```
businessFunction  = (R) -> (P) -> A 
```

Where:
 **R**  - is an environment / infrastructure that a function may use
 **P** - is a *tracked* parameter to function (optional)
 **A** - is a result of the function

This is very generic way to present any piece of work.

In order to use NEE we have to represent our logic in this form - which is mostly easy.
(we will show it later)


## Putting inside NEE Monad

Next step is to put business function inside NEE monad. Why?
Because NEE moands connects business logic with infrastructure. After all
you want to use your code on a real hardware with some nasty side effects.

```
NEE.pure(Nop)(businessFunction)
```

Above there is  how businessFunctions is blessed with side effects and now is 
wrapped inside NEE monad. (Actually this looks as an applicative functor - but it does not matter).

As for side effects we see Nop... meaning not a real one - but it is time to tell more about `Effects`

## Effects

 Effect is a special class that tells how to connect businessFunction with a reality.
 
 ```
 interface Effect<R, E> {
     fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Out<E, A>, R>
  }
```

In order to provide effect we need to implement interface as above.
Where:
 - **R** as before is some environment object, think this is how to get DB connection from,
 - **P** is a generic parameter that might be used by effect (actually it is only needed for caching)
 - **E** is an error that might happen during application of effect 
            (notice - it does not have to be Exception)
            
```Out``` is special object that represents the final result of calculation. 
Think of it as `Out<E,T> = Future<Either<E,T>>`

Lets see what effect does:
takes a function (businessFunction) which may rely on enviroment `R`, and on a  parameter `P`, 
giving  some result `A`. 
Then wraps it into a function that:
    takes environment `R` (no change), 
      -  later takes `P` and returns `Out` object (the result)
      -  it also returns   changed environment `(R)` - think that maybe transaction is now started            

## Monads

`NEE` is in fact a monad. This means that we can chain various business functions executions.
`Out` is also a monad. This means we can chain results.

### Explanation

If you want both methods to run inside same transaction 
```kotlin

```


if you want to run in separate transactions
```kotlin

```

Notice: first version is in fact even more flexible - allows for nested transactions.




# TODO
- Code:
    - remove warnings
    - code checker
    - style check
    - naming & long lambdas clean 
    
- Tests:
    - real assertions
    - unhappy paths
    - load tests (sanity)
