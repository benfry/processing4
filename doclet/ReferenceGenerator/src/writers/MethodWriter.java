package writers;

import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;

import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Tag;

import java.io.*;
import org.json.*;

public class MethodWriter extends BaseWriter {
	public MethodWriter(){}
	
	/**
	 * 
	 * 
	 * @param vars the inherited vars from the method's ClassDoc
	 * @param doc the method doc
	 * @throws IOException
	 */
	public static void write( HashMap<String, String> vars, MethodDoc doc, String classname, String foldername) throws IOException
	{
		String filename = getAnchor(doc);
		TemplateWriter templateWriter = new TemplateWriter();

		ArrayList<String> syntax = templateWriter.writeLoopSyntax("method.syntax.partial.html", getSyntax(doc, getInstanceName(doc)));

		JSONObject methodJSON = new JSONObject();

		String fileName = foldername + classname + "_" + getName(doc).replace("()", "_") + ".json";

		Tag[] tags = doc.tags(Shared.i().getWebrefTagName());
		String category = getCategory(tags[0]);
		String subcategory = getSubcategory(tags[0]);

		try
			{
				methodJSON.put("type", "function");
      			methodJSON.put("name", getName(doc));
      			methodJSON.put("description", getWebDescriptionFromSource(doc));
      			methodJSON.put("brief", getWebBriefFromSource(doc));
      			methodJSON.put("category", category);
      			methodJSON.put("subcategory", subcategory);
      			methodJSON.put("syntax", syntax);
      			methodJSON.put("parameters", getParameters(doc));
      			methodJSON.put("related", getRelated(doc));
      			methodJSON.put("returns", getReturnTypes(doc));
      		} catch (JSONException ex) 
      		{
      			ex.printStackTrace();
      		}

		try
			{
		if(Shared.i().isRootLevel(doc.containingClass())){
			methodJSON.put("classname", "");
		} else {
			methodJSON.put("classanchor", getLocalAnchor(doc.containingClass()));
		}
		      		} catch (JSONException ex) 
      		{
      			ex.printStackTrace();
      		}


		try {
         	FileWriter file = new FileWriter(fileName);
         	file.write(methodJSON.toString());
         	file.close();
      	} catch (IOException e) {
         	e.printStackTrace();
      	}
	}
	
}
