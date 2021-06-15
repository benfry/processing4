package writers;

import java.io.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Tag;

import org.json.*;

public class ClassWriter extends BaseWriter {
	private ClassDoc classDoc;
	private String libDir;
	
	public ClassWriter() {
		
	} 

	@SuppressWarnings("unchecked")
	public void write( ClassDoc classDoc ) throws IOException {
		if( needsWriting(classDoc) )
		{

			TemplateWriter templateWriter = new TemplateWriter();
			this.classDoc = classDoc;
			String classname = getName(classDoc);
			String anchor = getAnchor(classDoc);
			
			HashMap<String, String> vars = new HashMap<String, String>();

			JSONObject classJSON = new JSONObject();

			String fileName, folderName;
			if (libDir != null) {
				fileName = libDir + classname + ".json";
				folderName = libDir;
			}
			else {
				fileName = jsonDir + classname + ".json";
				folderName = jsonDir;
			}

			Tag[] tags = classDoc.tags(Shared.i().getWebrefTagName());
			String category = getCategory(tags[0]);
			String subcategory = getSubcategory(tags[0]);

			try
			{
				classJSON.put("type", "class");
				// These vars will be inherited by method and field writers
				classJSON.put("name", classname);
				classJSON.put("classanchor", anchor);
				String desc = getWebDescriptionFromSource(classDoc);
				if ( desc != "" ) 
				{
					classJSON.put( "description", desc );
				}
				if( !Shared.i().isCore(classDoc))
				{	// documenting a library
					classJSON.put("isLibrary", "true" );
					classJSON.put("csspath", "../../");
				}

				classJSON.put("brief", getWebBriefFromSource(classDoc));

				ArrayList<JSONObject> methodSet = new ArrayList<JSONObject>();
				ArrayList<JSONObject> fieldSet = new ArrayList<JSONObject>();
			
				// Write all @webref methods for core classes (the tag tells us where to link to it in the index)
			
				for (MethodDoc m : classDoc.methods()) {
					if(needsWriting(m)){
						if (!classname.equals("PGraphics") || getName(m).equals("beginDraw()") || getName(m).equals("endDraw()")) {
							MethodWriter.write((HashMap<String, String>)vars.clone(), m, classname, folderName);				
							methodSet.add(getPropertyInfo(m));
						}
					}
				}
				
				for (FieldDoc f : classDoc.fields()) {
					if(needsWriting(f)){
						FieldWriter.write((HashMap<String, String>)vars.clone(), f, classname);
						fieldSet.add(getPropertyInfo(f));				
					}
				}
				ArrayList<String> constructors = getConstructors();
				classJSON.put("category", category);
      			classJSON.put("subcategory", subcategory);
				classJSON.put("methods", methodSet);
				classJSON.put("classFields", fieldSet);
				classJSON.put("constructors", constructors);
				classJSON.put("parameters", getParameters(classDoc));
				classJSON.put("related", getRelated(classDoc));

			} catch (JSONException ex) 
      		{
      			ex.printStackTrace();
      		}

      		try {
         		FileWriter file = new FileWriter(fileName);
         		file.write(classJSON.toString());
         		file.close();
      		} catch (IOException e) {
         		e.printStackTrace();
      		}
		}
		
	}

	public void write( ClassDoc classDoc, String lib ) throws IOException {
		libDir = lib;
		write(classDoc);
	}
	
	private ArrayList<String> getConstructors()
	{
		ArrayList<String> constructors = new ArrayList<String>();
		for( ConstructorDoc c : classDoc.constructors() )
		{
			if(Shared.i().shouldOmit(c)){
				continue;
			}
			
			String constructor = c.name() + "(";
			
			for( Parameter p : c.parameters() )
			{
				constructor += p.name() + ", ";
			}
			if( constructor.endsWith(", ") )
			{
				constructor = constructor.substring(0, constructor.length()-2) + ")";
			}
			else {
				constructor += ")";
			}
			constructors.add(constructor);
		}
		return constructors;
	}

	private JSONObject getPropertyInfo(ProgramElementDoc doc) {
		JSONObject ret = new JSONObject();
		try
		{
			ret.put("name", getName(doc));
			ret.put("anchor", getLocalAnchor(doc));
			ret.put("desc", getWebBriefFromSource(doc));
		} catch (JSONException ex) 
      	{
      		ex.printStackTrace();
      	}
		return ret;
	}

}
