
## testkit

This is a set of utilities to make testing easier in Scala.

### Installation

```
libraryDependencies += "co.blocke" %% "testkit" % CURRENT_VERSION % Test 
```

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).

### ZioTestKit

Have you ever made a change to your code that breaks lots of existing tests? You then go through the arduous task of going through each test in each spec suite to fix them all. For me, this often means singling out 1 test at a time for debugging, and then to ensure I haven't broken previously-fixed tests I will uncomment all the tests up to the one I'm working on.

sbt has tagging along with non-intuitive commands I have trouble remembering for some of this but I wanted something much easier, so I created ZioTestKit, which works with zio test.  It provides ```@@ only``` and ```@@ until```.

Use it like this: (Don't overlook the @@ ziotestkit at the end of the suite definition!)

```scala
import zio.*  
import zio.test.*
import co.blocke.testkit.ZioTestKit._

object MyTestSpec extends ZIOSpecDefault:  
  def spec = suite("My Tests")(  
    test("test 1") {  
      // some test
    },  
    test("test 2") {  
      // some test
    } @@ only,
    test("test 3") {  
      // some test
    },
  ) @@ ziotestkit
```

In this example when this test is run, only "test 2" will execute. The others will be ignored.

```scala
import zio.*  
import zio.test.*
import co.blocke.testkit.ZioTestKit._

object MyTestSpec extends ZIOSpecDefault:  
  def spec = suite("My Tests")(  
    test("test 1") {  
      // some test
    },  
    test("test 2") {  
      // some test
    } @@ until,
    test("test 3") {  
      // some test
    },
  ) @@ ziotestkit
```

In this test "test 1" and "test 2" will be run.

If both ```@@ only``` and ```@@ until``` are present in a spec, the ```@@ only``` takes precedence. 

It's not profound... but it's profoundly useful!