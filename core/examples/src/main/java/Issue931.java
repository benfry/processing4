import processing.core.PApplet;

// Reproduction of issue #931
// Only seen on Windows
public class Issue931 extends PApplet {
    public void draw(){
        background(frameCount % 256);
        text("Hello World "+frameCount, 10, 10);

        frameRate(9999);
        surface.setSize(frameCount + 100, 100);
    }
    public static void main(String[] passedArgs) {
        String[] appletArgs = new String[]{ Issue931.class.getName()};
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }

    }
}
