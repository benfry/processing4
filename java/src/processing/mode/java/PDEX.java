package processing.mode.java;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

import processing.app.SketchCode;


public class PDEX {

  private InspectMode inspect;
  private ShowUsage usage;
  private Rename rename;

  private PreprocessingService pps;


  public PDEX(JavaEditor editor, PreprocessingService pps) {
    this.pps = pps;

    usage = new ShowUsage(editor, pps);
    inspect = new InspectMode(editor, pps, usage);
    rename = new Rename(editor, pps, usage);

    for (SketchCode code : editor.getSketch().getCode()) {
      Document document = code.getDocument();
      addDocumentListener(document);
    }

    sketchChangedX();
  }


  public void addDocumentListener(Document doc) {
    if (doc != null) {
      doc.addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          sketchChangedX();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          sketchChangedX();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          sketchChangedX();
        }
      });
    }
  }


  public void sketchChangedX() {
    errorChecker.notifySketchChanged();
    pps.notifySketchChanged();
  }


  public void dispose() {
    inspect.dispose();
    usage.dispose();
    rename.dispose();
  }
}
