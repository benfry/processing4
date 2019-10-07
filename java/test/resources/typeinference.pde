import java.util.*;
import java.util.function.*;

void setup() {
  List<String> list = new ArrayList<>();
  list.add("line1\nline2");
  list.add("line3");

  // Local variable type inference in loop
  for (var s : list) {
    println(s);
  }

  // Local variable type inference
  var testString = list.get(0);
  println(testString.lines().count()); // Java 11 API
}
