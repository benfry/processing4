package processing.core.platform;

public class Fenster {
  static void load() {
    System.out.println("about to load");
    System.loadLibrary("fenster");
    System.out.println("loaded");
  }

  static void info() {
    System.out.println("about to init");
    Fenster f = new Fenster();
    // f.sayHello();
    System.out.println("about to call");
    int ppi = f.getLogPixels();
    System.out.println("getLogPixels = " + ppi);
    System.out.println("aka " + (ppi / 96f));
    System.out.println("done");
  }

  public static void main(String[] args) {
    new Thread(() -> {
    // java.awt.EventQueue.invokeLater(() -> {
      load();
      info();
    // });
    }).start();
  }

  private native void sayHello();

  private native int getLogPixels();
}
