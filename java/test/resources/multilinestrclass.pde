class TestClass {
  final String testMultiline1 = """
line1 "
line 2 ""
line  3
line   4""";

  String getStr() {
    return testMultiline1;
  }
}


void setup() {
  TestClass test = new TestClass();
  println(test.getStr());
}

void draw() {
}
