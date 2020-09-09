package writers;

import java.io.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Tag;
import org.json.*;

public class FunctionWriter extends BaseWriter {
	
	static ArrayList<String> writtenFunctions;
	
	public FunctionWriter(){}
	
	public static void write(MethodDoc doc) throws IOException
	{
		if( needsWriting(doc)){			
			String anchor = getAnchor(doc);
			TemplateWriter templateWriter = new TemplateWriter();
			
			ArrayList<String> syntax = templateWriter.writeLoopSyntax("function.syntax.partial.html", getSyntax(doc, ""));

			JSONObject functionJSON = new JSONObject();

			String fileName = jsonDir + getName(doc).replace("()", "_") + ".json";

			Tag[] tags = doc.tags(Shared.i().getWebrefTagName());
			String category = getCategory(tags[0]);
			String subcategory = getSubcategory(tags[0]);

			try
			{
				functionJSON.put("type", "function");
      			functionJSON.put("name", getName(doc));
      			functionJSON.put("description", getWebDescriptionFromSource(doc));
      			functionJSON.put("brief", getWebBriefFromSource(doc));
      			functionJSON.put("category", category);
      			functionJSON.put("subcategory", subcategory);
      			functionJSON.put("syntax", syntax);
      			functionJSON.put("parameters", getParameters(doc));
      			functionJSON.put("related", getRelated(doc));
      			functionJSON.put("returns", getReturnTypes(doc));
      		} catch (JSONException ex) 
      		{
      			ex.printStackTrace();
      		}

      		try {
         		FileWriter file = new FileWriter(fileName);
         		file.write(functionJSON.toString());
         		file.close();
      		} catch (IOException e) {
         		e.printStackTrace();
      		}
			
		}
		
	}	
}
