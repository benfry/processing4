// Thanks StanLepunK: https://github.com/processing/processing4/issues/317
Truc truc = new Truc();

void setup() {
  size(200,200);
  truc.size(1,1); // problem >>> error à "."
  // func();
}

void draw() {
  truc.size(1,1); // no problem
}

void func() {
  truc.size(1,1); // no problem
}

class Truc {
  void size(int x, int y) {
  }
}
