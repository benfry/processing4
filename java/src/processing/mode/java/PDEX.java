package processing.mode.java;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

import processing.app.SketchCode;


public class PDEX {
  private boolean enabled = true;

  private ErrorChecker errorChecker;

  private InspectMode inspect;
  private ShowUsage usage;
  private Rename rename;

  private PreprocessingService pps;


  public PDEX(JavaEditor editor, PreprocessingService pps) {
    this.pps = pps;

    this.enabled = !editor.hasJavaTabs();

    errorChecker = new ErrorChecker(editor, pps);

    usage = new ShowUsage(editor, pps);
    inspect = new InspectMode(editor, pps, usage);
    rename = new Rename(editor, pps, usage);

    for (SketchCode code : editor.getSketch().getCode()) {
      Document document = code.getDocument();
      addDocumentListener(document);
    }

    sketchChanged();
  }


  public void addDocumentListener(Document doc) {
    if (doc != null) {
      doc.addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          sketchChanged();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          sketchChanged();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          sketchChanged();
        }
      });
    }
  }


  public void sketchChanged() {
    errorChecker.notifySketchChanged();
    pps.notifySketchChanged();
  }


  public void preferencesChanged() {
    errorChecker.preferencesChanged();
    sketchChanged();
  }


  public void hasJavaTabsChanged(boolean hasJavaTabs) {
    enabled = !hasJavaTabs;
    if (!enabled) {
      usage.hide();
    }
  }


  public void dispose() {
    inspect.dispose();
    errorChecker.dispose();
    usage.dispose();
    rename.dispose();
  }


  public void documentChanged(Document newDoc) {
    addDocumentListener(newDoc);
  }
}
