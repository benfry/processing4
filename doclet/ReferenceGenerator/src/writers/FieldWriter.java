package writers;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.*;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.Tag;

import org.json.*;


public class FieldWriter extends BaseWriter {
	
	/**
	 * 
	 * @param vars inherited from containing ClassDoc
	 * @param doc
	 * @throws IOException
	 */
	
	public static void write(HashMap<String, String> vars, FieldDoc doc, String classname) throws IOException
	{
		TemplateWriter templateWriter = new TemplateWriter();

		JSONObject fieldJSON = new JSONObject();

		String fieldName;
		if (getName(doc).contains("[]"))  {
			fieldName = getName(doc).replace("[]", "");
		} else {
			fieldName = getName(doc);
		}

		String fileName;
		if (classname != "") {
			fileName = jsonDir + classname + "_" + fieldName + ".json";
		} else {
			fileName = jsonDir + fieldName + ".json";
		}

		Tag[] tags = doc.tags(Shared.i().getWebrefTagName());
		String category = getCategory(tags[0]);
		String subcategory = getSubcategory(tags[0]);

		try
		{
			fieldJSON.put("description", getWebDescriptionFromSource(doc));
			fieldJSON.put("brief", getWebBriefFromSource(doc));
			fieldJSON.put("category", category);
      		fieldJSON.put("subcategory", subcategory);
			fieldJSON.put("name", getName(doc));
			fieldJSON.put("related", getRelated(doc));
		
			if(Shared.i().isRootLevel(doc.containingClass())){
				fieldJSON.put("type", "other");
			} else {
				fieldJSON.put("type", "field");
				fieldJSON.put("classanchor", getLocalAnchor(doc.containingClass()));
				fieldJSON.put("parameters", getParentParam(doc));		
				String syntax = templateWriter.writePartial("field.syntax.partial", getSyntax(doc));
				ArrayList<String> syntaxList = new ArrayList<String>();
				syntaxList.add(syntax);
				fieldJSON.put("syntax", syntaxList);	
			}
		} catch (JSONException ex) 
      	{
      		ex.printStackTrace();
      	}

      	try {
         	FileWriter file = new FileWriter(fileName);
         	file.write(fieldJSON.toString());
         	file.close();
      	} catch (IOException e) {
         	e.printStackTrace();
      	}
		
	}
	
	public static void write(FieldDoc doc) throws IOException
	{
		String classname = "";
		write(new HashMap<String, String>(), doc, classname);
	}
	
	protected static HashMap<String, String> getSyntax(FieldDoc doc){
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("object", getInstanceName(doc));
		map.put("field", getName(doc));
		return map;
	}

	protected static ArrayList<JSONObject> getParentParam(FieldDoc doc){
		ArrayList<JSONObject> retList = new ArrayList<JSONObject>();
		JSONObject ret = new JSONObject();
		try
		{
			ret.put("name", getInstanceName(doc));
			ret.put("desc", getInstanceDescription(doc));
		} catch (JSONException ex) 
      	{
      		ex.printStackTrace();
      	}
      	retList.add(ret);
		return retList;
	}
	
	protected static HashMap<String, String> getParent(FieldDoc doc){
		HashMap<String, String> parent = new HashMap<String, String>();
		ClassDoc cd = doc.containingClass();
		parent.put("name", getInstanceName(doc));
		parent.put("name", getInstanceName(doc));
	 	parent.put("type", cd.name());
		parent.put("description", getInstanceDescription(doc));
		return parent;
	}

}
