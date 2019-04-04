{backmatter}

# Tabla de typeclasses

| Typeclass          | Método          | Desde           | Dado                   | Hacia          |
|--------------------|-----------------|-----------------|------------------------|----------------|
| `InvariantFunctor` | `xmap`          | `F[A]`          | `A => B, B => A`       | `F[B]`         |
| `Contravariant`    | `contramap`     | `F[A]`          | `B => A`               | `F[B]`         |
| `Functor`          | `map`           | `F[A]`          | `A => B`               | `F[B]`         |
| `Apply`            | `ap` / `<*>`    | `F[A]`          | `F[A => B]`            | `F[B]`         |
|                    | `apply2`        | `F[A], F[B]`    | `(A, B) => C`          | `F[C]`         |
| `Alt`              | `altly2`        | `F[A], F[B]`    | `(A \/ B) => C`        | `F[C]`         |
| `Divide`           | `divide2`       | `F[A], F[B]`    | `C => (A, B)`          | `F[C]`         |
| `Decidable`        | `choose2`       | `F[A], F[B]`    | `C => (A \/ B)`        | `F[C]`         |
| `Bind`             | `bind` / `>>=`  | `F[A]`          | `A => F[B]`            | `F[B]`         |
|                    | `join`          | `F[F[A]]`       |                        | `F[A]`         |
| `Cobind`           | `cobind`        | `F[A]`          | `F[A] => B`            | `F[B]`         |
|                    | `cojoin`        | `F[A]`          |                        | `F[F[A]]`      |
| `Applicative`      | `point`         | `A`             |                        | `F[A]`         |
| `Divisible`        | `conquer`       |                 |                        | `F[A]`         |
| `Comonad`          | `copoint`       | `F[A]`          |                        | `A`            |
| `Semigroup`        | `append`        | `A, A`          |                        | `A`            |
| `Plus`             | `plus` / `<+>`  | `F[A], F[A]`    |                        | `F[A]`         |
| `MonadPlus`        | `withFilter`    | `F[A]`          | `A => Boolean`         | `F[A]`         |
| `Align`            | `align`         | `F[A], F[B]`    |                        | `F[A \&/ B]`   |
|                    | `merge`         | `F[A], F[A]`    |                        | `F[A]`         |
| `Zip`              | `zip`           | `F[A], F[B]`    |                        | `F[(A, B)]`    |
| `Unzip`            | `unzip`         | `F[(A, B)]`     |                        | `(F[A], F[B])` |
| `Cozip`            | `cozip`         | `F[A \/ B]`     |                        | `F[A] \/ F[B]` |
| `Foldable`         | `foldMap`       | `F[A]`          | `A => B`               | `B`            |
|                    | `foldMapM`      | `F[A]`          | `A => G[B]`            | `G[B]`         |
| `Traverse`         | `traverse`      | `F[A]`          | `A => G[B]`            | `G[F[B]]`      |
|                    | `sequence`      | `F[G[A]]`       |                        | `G[F[A]]`      |
| `Equal`            | `equal` / `===` | `A, A`          |                        | `Boolean`      |
| `Show`             | `shows`         | `A`             |                        | `String`       |
| `Bifunctor`        | `bimap`         | `F[A, B]`       | `A => C, B => D`       | `F[C, D]`      |
|                    | `leftMap`       | `F[A, B]`       | `A => C`               | `F[C, B]`      |
|                    | `rightMap`      | `F[A, B]`       | `B => C`               | `F[A, C]`      |
| `Bifoldable`       | `bifoldMap`     | `F[A, B]`       | `A => C, B => C`       | `C`            |
| (con `MonadPlus`)  | `separate`      | `F[G[A, B]]`    |                        | `(F[A], F[B])` |
| `Bitraverse`       | `bitraverse`    | `F[A, B]`       | `A => G[C], B => G[D]` | `G[F[C, D]]`   |
|                    | `bisequence`    | `F[G[A], G[B]]` |                        | `G[F[A, B]]`   |

# Haskell

La documentación de Scalaz con frecuencia cita de librerías o artículos escritos
para el lenguaje de programación Haskell. En este breve capítulo, aprenderemos
suficiente Haskell para ser capaces de entender el material original, y para
asistir a charlas de Haskell en conferencias de programación funcional.

## Data

Haskell tiene una sintaxis muy limpia para las ADTs. Esta es una estructura de
lista ligada.

```haskell
  data List a = Nil | Cons a (List a)
```

`List` es un *constructor de tipos*, `a` es el *parámetro de tipo*, `|` separa
los *constructores de datos*, que son: `Nil` la lista vacía y una celda `Cons`.
`Cons` toma dos parámetros, que están separados por espacios en blanco: sin
comas ni paréntesis para los parámetros.

No existen las subclases en Haskell, de modo que no hay tal cosa como el tipo
`Nil` o el tipo `Cons`: ambos construyen una `List`.

Una (posible) traducción a Scala sería:

```scala
  sealed abstract class List[A]
  object Nil {
    def apply[A]: List[A] = ...
    def unapply[A](as: List[A]): Option[Unit] = ...
  }
  object Cons {
    def apply[A](head: A, tail: List[A]): List[A] = ...
    def unapply[A](as: List[A]): Option[(A, List[A])] = ...
  }
```

Es decir, el constructor de tipo es algo como `sealed abstract class`, y cada
constructor de datos es `.apply` / `.unapply`. Note que Scala no realiza un
emparejamiento de patrones exhaustivo sobre esta codificación, por lo que Scalaz
no lo usa.

Podemos usar notación infija: una definición más agradable podría usar el
símbolo `:.` en lugar de `Cons`

```haskell
  data List t = Nil | t :. List t
  infixr 5 :.
```

donde especificamos una fijeza (*fixity*), que puede ser

- `infix`: sin asociatividad
- `infixl`: asociatividad a la izquierda
- `infixr`: asociatividad a la derecha

Un número de 0 (baja) a 9 (alta) especifica la precedencia. Ahora podemos crear
una lista de enteros al teclear

```haskell
  1 :. 2 :. Nil
```

Haskell ya tiene una estructura de lista ligada, que tan fundamental en la
programación funcional que se le ha asignado una sintaxis con corchetes
cuadrados `[a]`

```haskell
  data [] a = [] | a : [a]
  infixr 5 :
```

y un constructor conveniente de valores multi-argumentos: `[1, 2, 3]` en lugar
de `1 : 2 : 3 : []`

Al final nuestras ADTs tendrán que almacenar valores primitivos. Los tipos de
datos primitivos más comunes son:

- `Char`: un caracter unicode
- `Text`: para bloques de texto unicode
- `Int`: un entero con signo de precisión fija, dependiente de la máquina
- `Word`: un `Int` sin signo, y de tamaños fijos `Word8` / `Word16` / `Word32` /
  `Word64`
- `Float` / `Double`: Números de precisión IEEE sencilla y doble
- `Integer` / `Natural`: enteros de precisión arbitraria con y sin signo,
  respectivamente
- `(,)`: tuplas, desde 0 (también conocido como *unit*) hasta 62 campos
- `IO` inspiración para la `IO` de Scalaz, implementada para el entorno de
  ejecución

con menciones honoríficas para

```haskell
  data Bool       = True | False
  data Maybe a    = Nothing | Just a
  data Either a b = Left a  | Right b
  data Ordering   = LT | EQ | GT
```

Como Scala, Haskell tiene aliases de tipo: un alias o su forma expandida pueden
ser usados de forma intercambiable. Por razones históricas, `String` está
definido como una lista ligada de `Char`

```haskell
type String = [Char]
```

que es muy ineficiente y siempre desearemos usar `Text` más bien.

Finalmente, es posible definir nombres de campos en las ADTs usando *sintaxis de
registros*, lo que significa que podemos crear los constructores de datos con
llaves y usar *anotaciones de tipo* con dos puntos dobles para indicar los tipos

```haskell
  -- ADT desnuda
  data Resource = Human Int String
  data Company  = Company String [Resource]

  -- Usando la sintaxis de registros
  data Resource = Human
                  { serial    :: Int
                  , humanName :: String
                  }
  data Company  = Company
                  { companyName :: String
                  , employees   :: [Resource]
                  }
```

Note que el constructor de datos `Human` y el tipo `Resource` no tienen el mismo
nombre. La sintaxis de registro genera el equivalente a un método para acceder a
los campos y un método para hacer copias

```haskell
  -- construct
  adam = Human 0 Adam
  -- field access
  serial adam
  -- copy
  eve = adam { humanName = "Eve" }
```

Una alternativa más eficiente que las definiciones `data` de un único campo es
usar un `newtype`, que no tiene costo adicional en tiempo de ejecución:

```haskell
  newtype Alpha = Alpha { underlying :: Double }
```

equivalente a `extends AnyVal` pero sin sus problemas.

A> Una limitación de la sintaxis de registros de Haskell es que un nombre de
A> campo no puede ser usada en tipos de datos diferentes. Sin embargo, podemos
A> solucionar esto al habilitar una extensión `LANGUAGE`, permitiéndonos usar
A> `name` tanto en `Human` y `Company`
A>
A> ```haskell
A> {-# LANGUAGE DuplicateRecordFields #-}
A>
A> data Resource = Human
A>                 { serial :: Int
A>                 , name   :: String
A>                 }
A> data Company  = Company
A>                 { name      :: String
A>                 , employees :: [Resource]
A>                 }
A> ```
A>
A> Existen muchas extensiones al lenguaje y no es poco común tener 20 o más en
A> un proyecto pequeño. Haskell es extremadamente conservador y las
A> características nuevas del lenguaje son opcionales por un largo periodo antes
A> de que puedan ser aceptadas en el estándar del lenguaje.

## Funciones

Aunque no es necesario, es una buena práctica escribir explícitamente la firma
de una función: su nombre seguido de su tipo. Por ejemplo `fold` especializada
para una lista ligada

```haskell
  foldl :: (b -> a -> b) -> b -> [a] -> b
```

Todas las funciones usan *currying* en Haskell, cada parámetro está separado por
una `->` y el tipo final es el tipo de retorno. La firma anterior es equivalente
a la siguiente firma de Scala:

```scala
  def foldLeft[A, B](f: (B, A) => B)(b: B)(as: List[A]): B
```

Algunas observaciones:

- no se ocupan palabras reservadas
- no hay necesidad de declarar los tipos que se introducen
- no hay necesidad de nombrar los parámetros

lo que resulta en código conciso.

Las funciones infijas están definidas en paréntesis y requieren de una
definición de fijeza:

```haskell
  (++) :: [a] -> [a] -> [a]
  infixr 5 ++
```

Las funciones normales pueden invocarse usando notación infija al rodear su
nombre con comillas. Las dos formas siguientes son equivalentes:

```haskell
  a `foo` b
  foo a b
```

Una función infija puede invocarse como una función normal si ponemos su nombre
entre paréntesis, y puede aplicarse *currying* ya sea por la izquierda o por la
derecha, con frecuencia resultando en semántica distinta:

```haskell
  invert = (1.0 /)
  half   = (/ 2.0)
```

Las funciones con frecuencia se escriben con el parámetro más general al
principio, para habilitar un máximo reutilización de las formas que usan
*currying*.

La definición de una función puede usar emparejamiento de patrones, con una
linea por caso. Ahí es posible nombrar los parámetros usando los constructores
de datos para extraer los parámetros, de manera muy similar a una cláusula
`case` de Scala:

```haskell
  fmap :: (a -> b) -> Maybe a -> Maybe b
  fmap f (Just a) = Just (f a)
  fmap _ Nothing  = Nothing
```

Los guiones bajos sirven para indicar parámetros que son ignorados y los nombres
de las funciones pueden estar en posición infija:

```haskell
  (<+>) :: Maybe a -> Maybe a -> Maybe a
  Just a <+> _      = Just a
  Empty  <+> Just a = Just a
  Empty  <+> Empty  = Empty
```

Podemos definir funciones lambda anónimas con una diagonal invertida, que se
parece a la letra griega λ. Las siguientes expresiones son equivalentes:

```haskell
  (*)
  (\a1 -> \a2 -> a1 * a2)
  (\a1 a2     -> a1 * a2)
```

Las funciones de Haskell sobre las que se hace un emparejamiento de patrones son
conveniencias sintácticas para funciones lambda anidadas. Considere una función
simple que crea una tupla dadas tres entradas:

```haskell
  tuple :: a -> b -> c -> (a, b, c)
```

La implementación

```haskell
  tuple a b c = (a, b, c)
```

es equivalente a

```haskell
  tuple = \a -> \b -> \c -> (a, b, c)
```

En el cuerpo de una función podemos crear asignaciones locales de valores con
cláusulas `let` o `where`. Las siguientes son definiciones equivalentes de `map`
para una lista ligada (un apóstrofe es un caracter válido en los identificadores
de nombre):

```haskell
  map :: (a -> b) -> [a] -> [b]

  -- explicit
  map f as = foldr map' [] as
             where map' a bs = f a : bs

  -- terser, making use of currying
  map f    = foldr map' []
             where map' a = (f a :)

  -- let binding
  map f    = let map' a = (f a :)
             in foldr map' []

  -- actual implementation
  map _ []       = []
  map f (x : xs) = f x : map f xs
```

`if` / `then` / `else` son palabras reservadas para sentencias condicionales:

```haskell
  filter :: (a -> Bool) -> [a] -> [a]
  filter _ [] = []
  filter f (head : tail) = if f head
                           then head : filter f tail
                           else filter f tail
```

Un estilo alternativo es el uso de *guardas de casos*

```haskell
  filter f (head : tail) | f head    = head : filter f tail
                         | otherwise = filter f tail
```

El emparejamiento de patrones sobre cualquier término se hace con `case ... of`

```haskell
  unfoldr :: (a -> Maybe (b, a)) -> a -> [b]
  unfoldr f b = case f b of
                  Just (b', a') -> b' : unfoldr f a'
                  Nothing       -> []
```

Las guardas pueden usarse dentro de los emparejamientos. Por ejemplo, digamos
que deseamos tratar a los ceros como un caso especial:

```haskell
  unfoldrInt :: (a -> Maybe (Int, a)) -> a -> [Int]
  unfoldrInt f b = case f b of
                     Just (i, a') | i == 0    -> unfoldrInt f a'
                                  | otherwise -> i : unfoldrInt f a'
                     Nothing                  -> []
```

Finalmente, dos funciones que vale la pena mencionar son `($)` y `(.)`

```haskell
  -- operador de aplicación
  ($) :: (a -> b) -> a -> b
  infixr 0

  -- composición de funciones
  (.) :: (b -> c) -> (a -> b) -> a -> c
  infixr 9
```

Ambas funciones son alternativas de estilo a los paréntesis anidados.

Las siguientes funciones son equivalentes:

```haskell
  Just (f a)
  Just $ f a
```

así como lo son

```haskell
  putStrLn (show (1 + 1))
  putStrLn $ show $ 1 + 1
```

Existe una tendencia a preferir la composición de funciones con `.` en lugar de
múltiples `$`

```haskell
  (putStrLn . show) $ 1 + 1
```

## Typeclasses

Para definir una typeclass usamos la palabra reservada `class`, seguida del
nombre de la typeclass, su parámetro de tipo, y entonces los miembros requeridos
un una cláusula `where`.

Si existen dependencias entre las typeclasses, por ejemplo, el hecho de que un
`Applicative` requiere la existencia de un `Functor`, llamamos a esta una
*restricción* y usamos la notación `=>`:

```haskell
  class Functor f where
    (<$>) :: (a -> b) -> f a -> f b
    infixl 4 <$>

  class Functor f => Applicative f where
    pure  :: a -> f a
    (<*>) :: f (a -> b) -> f a -> f b
    infixl 4 <*>

  class Applicative f => Monad f where
    (=<<) :: (a -> f b) -> f a -> f b
    infixr 1 =<<
```

Proporcionamos una implementación de una typeclass con la palabra reservada
`instance`. Si deseamos repetir la firma en las funciones instancias, útiles por
claridad, debemos habilitar la extensión del lenguaje `InstanceSigs`.

```haskell
  {-# LANGUAGE InstanceSigs #-}

  data List a = Nil | a :. List a

  -- definidos en otra parte
  (++) :: List a -> List a -> List a
  map :: (a -> b) -> List a -> List b
  flatMap :: (a -> List b) -> List a -> List b
  foldLeft :: (b -> a -> b) -> b -> List a -> b

  instance Functor List where
    (<$>) :: (a -> b) -> List a -> List b
    f <$> as = map f as

  instance Applicative List where
    pure a = a :. Nil

    Nil <*> _  = Nil
    fs  <*> as = foldLeft (++) Nil $ (<$> as) <$> fs

  instance Monad List where
    f =<< list = flatMap f list
```

Si tenemos una restricción de typeclass en una función, usamos la misma notación
`=>`. Por ejemplo podemos definir algo similar al `Apply.apply2` de Scalaz

```haskell
  apply2 :: Applicative f => (a -> b -> c) -> f a -> f b -> f c
  apply2 f fa fb = f <$> fa <*> fb
```

Dado que hemos introducido `Monad`, es un punto para introducir la notación
`do`, que fue la inspiración de las comprensiones `for` de Scala:

```haskell
  do
    a <- f
    b <- g
    c <- h
    pure (a, b, c)
```

que es equivalente a

```haskell
  f >>= \a ->
    g >>= \b ->
      h >>= \c ->
        pure (a, b, c)
```

donde `>>=` es `=<<` con los parámetros en orden contrario

```haskell
  (>>=) :: Monad f => f a -> (a -> f b) -> f b
  (>>=) = flip (=<<)
  infixl 1 >>=

  -- from the stdlib
  flip :: (a -> b -> c) -> b -> a -> c
```

A diferencia de Scala, no es necesario crear variables para los valores unit, o
proporcionar un `yield` si estamos devolviendo `()`. Por ejemplo

```scala
  for {
    _ <- putStr("hello")
    _ <- putStr(" world")
  } yield ()
```

se traduce como

```haskell
  do putStr "hello"
     putStr " world"
```

Los valores que no son monádicos pueden crearse con la palabra reservada `let`:

```haskell
  nameReturn :: IO String
  nameReturn = do putStr "What is your first name? "
                  first <- getLine
                  putStr "And your last name? "
                  last  <- getLine
                  let full = first ++ " " ++ last
                  putStrLn ("Pleased to meet you, " ++ full ++ "!")
                  pure full
```

Finalmente, Haskell tiene derivación de typeclasses con la palabra reservada
`deriving`, la inspiración para `@scalaz.deriving`. Definir las reglas de
derivación es un tema avanzado, pero es fácil derivar una typeclass para una
ADT:

```haskell
  data List a = Nil | a :. List a
                deriving (Eq, Ord)
```

## Algebras

En Scala, las typeclasses y las álgebras se definen como una interfaz `trait`,
Las typeclasses son inyectadas con la característica `implicit` y las álgebras
se pasan como parámetros explícitos. No hay soporte a nivel de Haskell para las
álgebras: ¡son simplemente datos!

Considere el álgebra simple `Console` de la introducción. Podemos reescribirla
en Haskell como un *registro de funciones*:

```haskell
  data Console m = Console
                    { println :: Text -> m ()
                    , readln  :: m Text
                    }
```

con la lógica de negocios usando una restricción monádica

```haskell
  echo :: (Monad m) => Console m -> m ()
  echo c = do line <- readln c
              println c line
```

Una implementación de producción para `Console` probablemente tendría tipo
`Console IO`. La función `liftIO` de Scalaz está inspirada en una función de
Haskell del mismo nombre y puede elevar `Console IO` a cualquier pila de Mónadas
Avanzadas.

Dos extensiones adicionales del lenguaje hacen que la lógica de negocio sea aún
más limpia. Por ejemplo, `RecordWildCards` permite importar todos los campos de
un tipo mediante el uso de `{..}`:

```haskell
  echo :: (Monad m) => Console m -> m ()
  echo Console{..} = do line <- readln
                        println line
```

`NamedFieldPuns` requiere que cada campo importado sea listado explícitamente,
lo que es más código verboso pero hace que el código sea más fácil de leer:

```haskell
  echo :: (Monad m) => Console m -> m ()
  echo Console{readln, println} = do line <- readln
                                     println line
```

Mientras en Scala esta codificación podría llamarse *Finalmente sin etiquetas*,
en Haskell es conocida como estilo MTL. Sin entrar en detalles, algunos
desarrolladores de Scala no comprendieron un artículo de investigación sobre los
beneficios de rendimiento de las [ADTs generalizadas en
Haskell](http://okmij.org/ftp/tagless-final/index.html#tagless-final).

Una alternativa al estilo MTL son los *Efectos extensibles*, también conocido
como [estilo de Mónada libre](http://okmij.org/ftp/Haskell/extensible/more.pdf).

## Módulos

El código fuente de Haskell está organizado en módulos jerárquicos con la
restricción de que todo el contenido de un `module` debe vivir en un único
archivo. En la parte superior de un archivo se declara el nombre del `module`

```haskell
  module Silly.Tree where
```

Una convención es usar directorios en el disco para organizar el código, de modo
que este archivo iría en `Silly/Tree.hs`.

Por defecto todos los símbolos en el archivo son exportados pero podemos escoger
exportar miembros específicos, por ejemplo el tipo `Tree` y los constructores de
datos, y una función `fringe`, omitiendo `sapling`:

```haskell
  module Silly.Tree (Tree(..), fringe) where

  data Tree a = Leaf a | Branch (Tree a) (Tree a)

  fringe :: Tree a -> [a]
  fringe (Leaf x)            = [x]
  fringe (Branch left right) = fringe left ++ fringe right

  sapling :: Tree String
  sapling = Leaf ""
```

De manera interesante, podemos exportar símbolos que son importados al módulo,
permitiendo que los autores de librerías empaquen su API completa en un único
módulo, sin importar cómo fue implementada.

En un archivo distinto podemos importar todos los miembros exportados desde
`Silly.Tree`

```haskell
  import Silly.Tree
```

que es aproximadamente equivalente a la sintaxis de Scala `import silly.tree._`.
Si deseamos restringir los símbolos que importamos podemos proveer una lista
explícita entre paréntesis después del import

```haskell
  import Silly.Tree (Tree, fringe)
```

Aquí únicamente importamos el constructor de tipo `Tree` (no los constructores
de datos) y la función `fringe`. Si desamos importar todos los constructores de
datos (y los emparejadores de patrones) podemos usar `Tree(...)`. Si únicamente
deseamos importar el constructor `Branch` podemos listarlo explícitamente:

```haskell
  import Silly.Tree (Tree(Branch), fringe)
```

Si tenemos colisión de nombres sobre un símbolo podemos usar un import
`qualified`, con una lista opcional de símbolos a importar

```haskell
  import qualified Silly.Tree as T
```

Ahora podemos acceder a la función `fringe` con `T.fringe`.

De manera alternativa, más bien que seleccionar, podemos escoger que **no**
importar

```haskell
  import Silly.Tree hiding (fringe)
```

Por defecto el módulo `Prelude` es importado implícitamente pero si agregamos un
import explícito del módulo `Prelude`, únicamente nuestra versión es usada.
Podemos usar esta técnica para esconder funciones antiguas inseguras

```haskell
  import Prelude hiding ((!!), head)
```

o usar un preludio personalizado y deshabilitar el preludio por defecto con la
extensión del lenguaje `NoImplicitPrelude`.

## Evaluación

Haskell se compila a código nativo, no hay una máquina virtual, pero existe un
recolector de basura. Un aspecto fundamental del ambiente de ejecución es que
todos los parámetros se evalúan de manera perezosa por default. Haskell trata
todos los términos como una promesa de proporcionar un valor cuando sea
necesario, llamado un *thunk*. Los *thunks* se reducen tanto como sea necesario
para proceder, y no más.

¡Una enorme ventaja de la evaluación perezosa es que es mucho más complicado
ocasionar un sobreflujo de la pila! Una desventaja es que existe un costo
adicional comparado con la evaluación estricta, por lo que Haskell nos permite
optar por la evaluación estricta parámetro por parámetro.

Haskell también está matizado por lo que significa evaluación estricta: se dice
que un término está en su *forma normal de cabeza débil* (WHNF, por sus siglas
en inglés) si los bloques de código más externos no pueden reducirse más, y en
su *forma normal* si el término está completamente evaluado. La estrategia de
evaluación por defecto de Scala corresponde aproximadamente a la forma normal.

Por ejemplo, estos términos están en forma normal:

```haskell
  42
  (2, "foo")
  \x -> x + 1
```

mientras que los siguientes no están en forma normal (todavía pueden reducirse
más):

```haskell
  1 + 2            -- reduces to 3
  (\x -> x + 1) 2  -- reduces to 3
  "foo" ++ "bar"   -- reduces to "foobar"
  (1 + 1, "foo")   -- reduces to (2, "foo")
```

Los siguientes términos están en WHNF debido a que el código más externo no
puede reducirse más (aunque las partes internas puedan):

```haskell
  (1 + 1, "foo")
  \x -> 2 + 2
  'f' : ("oo" ++ "bar")
```

La estrategia de evaluación por defecto es no realizar reducción alguna cuando
se pasa un término como parámetro. El soporte a nivel de lenguaje nos permite
solicitar WHNF para cualquier término con `($!)`

```haskell
  -- evalúa `a` a WHNF, entonces invoca la función con dicho valor
  ($!) :: (a -> b) -> a -> b
  infixr 0
```

Podemos usar un signo de admiración `!` en los parámetros `data`:

```haskell
  data StrictList t = StrictNil | !t :. !(StrictList t)

  data Employee = Employee
                    { name :: !Text
                    , age :: !Int
                    }
```

La extensión `StrictData` permite parámetros estrictos para todos los datos en
un módulo.

Otra extensión, `BangPatterns`, permite que `!` sea usado en los argumentos de
las funciones. La extensión `Strict` hace todas las funciones y los parámetros
de datos en el módulo estrictos por defecto.

Yendo al extremo podemos usar `($!!)` y la typeclass `NFData` para evaluación en
su forma normal.

```haskell
  class NFData a where
    rnf :: a -> ()

  ($!!) :: (NFData a) => (a -> b) -> a -> b
```

que está sujeto a la disponibilidad de una instancia de `NFData`.

El costo de optar por ser estricto es que Haskell se comporta como cualquier
otro lenguaje estricto y puede realizar trabajo innecesario. Optar por la
evaluación estricta debe hacerse con mucho cuidado, y únicamente para mejoras de
rendimiento medibles. Si está en duda, sea perezoso y acepte las opciones por
defecto.

A> Exite un gran detalle con la evaluación perezosa: si se realiza una acción
A> I/O que llene una estructura de datos perezosa, la acción será ejecutada
A> cuando se evalúe la estructura de datos, lo que puede fallar en partes
A> inesperadas del código y fuera de la lógica de manejo de recursos. Para
A> evitar esta situación, sólo lea en estructuras de datos estrictas cuando
A> realice I/O.
A>
A> Felizmente, esta situación únicamente afecta a los desarrolladores que están
A> escribiendo código I/O de bajo nivel. Librerías de terceros como `pipes-safe`
A> y `conduits` proporcionan abstracciones seguras para el Haskeller típico. La
A> mayoría de las primitivas de bytes desnudos y `Text` son estrictas, con
A> variantes `Lazy`.

## Siguientes pasos

Haskell es un lenguaje más rápido, seguro y simple que Scala y ha sido probado
en la industria. Considere tomar el [curso de data61 sobre programación
funcional](https://github.com/data61/fp-course), y pregunte en el cuarto de
charla `#qfpl` en `freenode.net`.

Algunos materiales de aprendizaje adicionales son:

- [Programming in Haskell](http://www.cs.nott.ac.uk/~pszgmh/pih.html) para
  aprender Haskell a partir de principios primarios.
- [Parallel and Concurrent Programming in
  Haskell](http://shop.oreilly.com/product/0636920026365.do) y [What I Wish I
  Knew When Learning Haskell](http://dev.stephendiehl.com/hask/#data-kinds) para
  sabiduría intermedia.
- [Glasgow Haskell Compiler User
  Guide](https://downloads.haskell.org/~ghc/latest/docs/html/users_guide/) y
  [HaskellWiki](https://wiki.haskell.org) para los hechos duros.
- [Eta](https://eta-lang.org/), es decir Haskell para la JVM.

Si usted disfruta usar Haskell y entiende el valor que traería a su negocio,
¡dígale a sus managers! De esa manera, el pequeño porcentaje de managers que
lideran proyectos de Haskell serán capaces de atraer talento de programación
funcional de los muchos equipos que no, y todos serán felices.

# Licencias de terceros

Algo del código fuente de este libro ha sido copiado de proyectos de software
libre. La licencia de estos proyectos requieren que los siguientes textos se
distribuyan con el código que se presenta en este libro.

## Scala License

```text
  Copyright (c) 2002-2017 EPFL
  Copyright (c) 2011-2017 Lightbend, Inc.

  All rights reserved.

  Redistribution and use in source and binary forms, with or without modification,
  are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of the EPFL nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```


## Scalaz License

```text
  Copyright (c) 2009-2014 Tony Morris, Runar Bjarnason, Tom Adams,
                          Kristian Domagala, Brad Clow, Ricky Clarkson,
                          Paul Chiusano, Trygve Laugstøl, Nick Partridge,
                          Jason Zaugg
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
  3. Neither the name of the copyright holder nor the names of
     its contributors may be used to endorse or promote products derived from
     this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

<!-- LocalWords: infija
-->
