/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012 The Processing Foundation
  Copyright (c) 2009-12 Ben Fry and Casey Reas

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty
  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.data;

import java.io.*;

import javax.xml.parsers.*;

import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import processing.core.PApplet;


/**
 * <b>XML</b> is a representation of an <b>XML</b> object, able to parse <b>XML</b> code. Use
 * <b>loadXML()</b> to load external XML files and create <b>XML</b>
 * objects.<br />
 * <br />
 * Only files encoded as UTF-8 (or plain ASCII) are parsed properly; the
 * encoding parameter inside <b>XML</b> files is ignored.
 *
 * @webref data:composite
 * @webBrief This is the base class used for the Processing XML library,
 *           representing a single node of an <b>XML</b> tree
 * @see PApplet#loadXML(String)
 * @see PApplet#parseXML(String)
 * @see PApplet#saveXML(XML, String)
 */
public class XML implements Serializable {

  /** The internal representation, a DOM node. */
  protected Node node;

//  /** Cached locally because it's used often. */
//  protected String name;

  /** The parent element. */
  protected XML parent;

  /** Child elements, once loaded. */
  protected XML[] children;

  /**
   * @nowebref
   */
  protected XML() { }


//  /**
//   * Begin parsing XML data passed in from a PApplet. This code
//   * wraps exception handling, for more advanced exception handling,
//   * use the constructor that takes a Reader or InputStream.
//   *
//   * @throws SAXException
//   * @throws ParserConfigurationException
//   * @throws IOException
//   */
//  public XML(PApplet parent, String filename) throws IOException, ParserConfigurationException, SAXException {
//    this(parent.createReader(filename));
//  }


  /**
   * Advanced users only; use loadXML() in PApplet. This is not a supported
   * function and is subject to change. It is available simply for users that
   * would like to handle the exceptions in a particular way.
   *
   * @nowebref
   */
  public XML(File file) throws IOException, ParserConfigurationException, SAXException {
    this(file, null);
  }


  /**
   * Advanced users only; use loadXML() in PApplet.
   *
   * @nowebref
   */
  public XML(File file, String options) throws IOException, ParserConfigurationException, SAXException {
    this(PApplet.createReader(file), options);
  }

  /**
   * @nowebref
   */
  public XML(InputStream input) throws IOException, ParserConfigurationException, SAXException {
    this(input, null);
  }


  /**
   * Unlike the loadXML() method in PApplet, this version works with files
   * that are not in UTF-8 format.
   *
   * @nowebref
   */
  public XML(InputStream input, String options) throws IOException, ParserConfigurationException, SAXException {
    //this(PApplet.createReader(input), options);  // won't handle non-UTF8
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    try {
      // Prevent 503 errors from www.w3.org
      factory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    } catch (IllegalArgumentException e) {
      // ignore this; Android doesn't like it
    }

    factory.setExpandEntityReferences(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(new InputSource(input));
    node = document.getDocumentElement();
  }


  /**
   * Advanced users only; use loadXML() in PApplet.
   *
   * @nowebref
   */
  public XML(Reader reader) throws IOException, ParserConfigurationException, SAXException {
    this(reader, null);
  }


  /**
   * Advanced users only; use loadXML() in PApplet.
   *
   * Added extra code to handle \u2028 (Unicode NLF), which is sometimes
   * inserted by web browsers (Safari?) and not distinguishable from a "real"
   * LF (or CRLF) in some text editors (i.e. TextEdit on OS X). Only doing
   * this for XML (and not all Reader objects) because LFs are essential.
   * https://github.com/processing/processing/issues/2100
   *
   * @nowebref
   */
  public XML(final Reader reader, String options) throws IOException, ParserConfigurationException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    // Prevent 503 errors from www.w3.org
    try {
      factory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    } catch (IllegalArgumentException e) {
      // ignore this; Android doesn't like it
    }

    // without a validating DTD, this doesn't do anything since it doesn't know what is ignorable
//      factory.setIgnoringElementContentWhitespace(true);

    factory.setExpandEntityReferences(false);
//      factory.setExpandEntityReferences(true);

//      factory.setCoalescing(true);
//      builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    DocumentBuilder builder = factory.newDocumentBuilder();
//      builder.setEntityResolver()

//      SAXParserFactory spf = SAXParserFactory.newInstance();
//      spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
//      SAXParser p = spf.newSAXParser();

    //    builder = DocumentBuilderFactory.newDocumentBuilder();
    //    builder = new SAXBuilder();
    //    builder.setValidation(validating);

    Document document = builder.parse(new InputSource(new Reader() {
      @Override
      public int read(char[] cbuf, int off, int len) throws IOException {
        int count = reader.read(cbuf, off, len);
        for (int i = 0; i < count; i++) {
          if (cbuf[off+i] == '\u2028') {
            cbuf[off+i] = '\n';
          }
        }
        return count;
      }

      @Override
      public void close() throws IOException {
        reader.close();
      }
    }));
    node = document.getDocumentElement();
  }


  /**
   * @param name creates a node with this name
   *
   */
  public XML(String name) {
    try {
      // TODO is there a more efficient way of doing this? wow.
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.newDocument();
      node = document.createElement(name);
      this.parent = null;

    } catch (ParserConfigurationException pce) {
      throw new RuntimeException(pce);
    }
  }

  /**
   * @nowebref
   */
  protected XML(XML parent, Node node) {
    this.node = node;
    this.parent = parent;

    for (String attr : parent.listAttributes()) {
      if (attr.startsWith("xmlns")) {
        // Copy namespace attributes to the kids, otherwise this XML
        // can no longer be printed (or manipulated in most ways).
        // Only do this when it's an Element, otherwise it's trying to set
        // attributes on text notes (interstitial content).
        if (node instanceof Element) {
          setString(attr, parent.getString(attr));
        }
      }
    }
  }


  /**
   * Converts <b>String</b> content to an <b>XML</b> object
   *
   * @webref xml:method
   * @webBrief Converts <b>String</b> content to an <b>XML</b> object
   * @param data the content to be parsed as XML
   * @return an XML object, or null
   * @throws SAXException
   * @throws ParserConfigurationException
   * @throws IOException
   * @nowebref
   */
  static public XML parse(String data) throws IOException, ParserConfigurationException, SAXException {
    return XML.parse(data, null);
  }

  /**
   * @nowebref
   */
  static public XML parse(String data, String options) throws IOException, ParserConfigurationException, SAXException {
    return new XML(new StringReader(data), null);
  }


//  protected boolean save(OutputStream output) {
//    return write(PApplet.createWriter(output));
//  }


  public boolean save(File file) {
    return save(file, null);
  }


  public boolean save(File file, String options) {
    PrintWriter writer = PApplet.createWriter(file);
    boolean result = write(writer);
    writer.flush();
    writer.close();
    return result;
  }


  // Sends this object and its kids to a Writer with an indent of 2 spaces,
  // including the declaration at the top so that the output will be valid XML.
  public boolean write(PrintWriter output) {
    output.print(format(2));
    output.flush();
    return true;
  }


  /**
   * Gets a copy of the element's parent.  Returns the parent as another <b>XML</b> object.
   *
   * @webref xml:method
   * @webBrief Gets a copy of the element's parent
   */
  public XML getParent() {
    return this.parent;
  }

  /**
   * Internal function; not included in reference.
   */
  protected Object getNative() {
    return node;
  }


  /**
   * Gets the element's full name, which is returned as a <b>String</b>.
   *
   * @webref xml:method
   * @webBrief Gets the element's full name
   * @return the name, or null if the element only contains #PCDATA.
   */
  public String getName() {
//    return name;
    return node.getNodeName();
  }

  /**
   * Sets the element's name, which is specified as a <b>String</b>.
   *
   * @webref xml:method
   * @webBrief Sets the element's name
   */
  public void setName(String newName) {
    Document document = node.getOwnerDocument();
    node = document.renameNode(node, null, newName);
//    name = node.getNodeName();
  }


  /**
   * Returns the name of the element (without namespace prefix).
   *
   * Internal function; not included in reference.
   */
  public String getLocalName() {
    return node.getLocalName();
  }


  /**
   * Honey, can you just check on the kids? Thanks.
   *
   * Internal function; not included in reference.
   */
  protected void checkChildren() {
    if (children == null) {
      NodeList kids = node.getChildNodes();
      int childCount = kids.getLength();
      children = new XML[childCount];
      for (int i = 0; i < childCount; i++) {
        children[i] = new XML(this, kids.item(i));
      }
    }
  }


  /**
   * Returns the number of children.
   *
   * @webref xml:method
   * @webBrief Returns the element's number of children
   * @return the count.
   */
  public int getChildCount() {
    checkChildren();
    return children.length;
  }


  /**
   * Checks whether or not the element has any children, and returns the result as a <b>boolean</b>.
   *
   * @webref xml:method
   * @webBrief Checks whether or not an element has any children
   */
  public boolean hasChildren() {
    checkChildren();
    return children.length > 0;
  }


  /**
   * Get the names of all of the element's children, and returns the names as an 
   * array of <b>Strings</b>. This is the same as looping through and calling <b>getName()</b> 
   * on each child element individually.
   *
   * @webref xml:method
   * @webBrief Returns the names of all children as an array
   */
  public String[] listChildren() {
//    NodeList children = node.getChildNodes();
//    int childCount = children.getLength();
//    String[] outgoing = new String[childCount];
//    for (int i = 0; i < childCount; i++) {
//      Node kid = children.item(i);
//      if (kid.getNodeType() == Node.ELEMENT_NODE) {
//        outgoing[i] = kid.getNodeName();
//      } // otherwise just leave him null
//    }
    checkChildren();
    String[] outgoing = new String[children.length];
    for (int i = 0; i < children.length; i++) {
      outgoing[i] = children[i].getName();
    }
    return outgoing;
  }


  /**
   * Returns all of the element's children as an array of <b>XML</b> objects. When 
   * the <b>name</b> parameter is specified, then it will return all children 
   * that match that name or path. The path is a series of elements and 
   * sub-elements, separated by slashes.
   *
   * @webref xml:method
   * @webBrief Returns an array containing all child elements
   */
  public XML[] getChildren() {
//    NodeList children = node.getChildNodes();
//    int childCount = children.getLength();
//    XMLElement[] kids = new XMLElement[childCount];
//    for (int i = 0; i < childCount; i++) {
//      Node kid = children.item(i);
//      kids[i] = new XMLElement(this, kid);
//    }
//    return kids;
    checkChildren();
    return children;
  }


  /**
   * Returns the first of the element's children that matches the <b>name</b> 
   * parameter.  The name or path is a series of elements and sub-elements, 
   * separated by slashes.
   *
   * @webref xml:method
   * @webBrief Returns the child element with the specified index value or path
   */
  public XML getChild(int index) {
    checkChildren();
    return children[index];
  }


  /**
   * Get a child by its name or path.
   *
   * @param name element name or path/to/element
   * @return the first matching element or null if no match
   */
  public XML getChild(String name) {
    if (name.length() > 0 && name.charAt(0) == '/') {
      throw new IllegalArgumentException("getChild() should not begin with a slash");
    }
    if (name.indexOf('/') != -1) {
      return getChildRecursive(PApplet.split(name, '/'), 0);
    }
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      XML kid = getChild(i);
      String kidName = kid.getName();
      if (kidName != null && kidName.equals(name)) {
        return kid;
      }
    }
    return null;
  }


  /**
   * Internal helper function for getChild(String).
   *
   * @param items result of splitting the query on slashes
   * @param offset where in the items[] array we're currently looking
   * @return matching element or null if no match
   * @author processing.org
   */
  protected XML getChildRecursive(String[] items, int offset) {
    // if it's a number, do an index instead
    if (Character.isDigit(items[offset].charAt(0))) {
      XML kid = getChild(Integer.parseInt(items[offset]));
      if (offset == items.length-1) {
        return kid;
      } else {
        return kid.getChildRecursive(items, offset+1);
      }
    }
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      XML kid = getChild(i);
      String kidName = kid.getName();
      if (kidName != null && kidName.equals(items[offset])) {
        if (offset == items.length-1) {
          return kid;
        } else {
          return kid.getChildRecursive(items, offset+1);
        }
      }
    }
    return null;
  }


  /**
   * Get any children that match this name or path. Similar to getChild(),
   * but will grab multiple matches rather than only the first.
   *
   * @param name element name or path/to/element
   * @return array of child elements that match
   * @author processing.org
   */
  public XML[] getChildren(String name) {
    if (name.length() > 0 && name.charAt(0) == '/') {
      throw new IllegalArgumentException("getChildren() should not begin with a slash");
    }
    if (name.indexOf('/') != -1) {
      return getChildrenRecursive(PApplet.split(name, '/'), 0);
    }
    // if it's a number, do an index instead
    // (returns a single element array, since this will be a single match
    if (Character.isDigit(name.charAt(0))) {
      return new XML[] { getChild(Integer.parseInt(name)) };
    }
    int childCount = getChildCount();
    XML[] matches = new XML[childCount];
    int matchCount = 0;
    for (int i = 0; i < childCount; i++) {
      XML kid = getChild(i);
      String kidName = kid.getName();
      if (kidName != null && kidName.equals(name)) {
        matches[matchCount++] = kid;
      }
    }
    return (XML[]) PApplet.subset(matches, 0, matchCount);
  }


  protected XML[] getChildrenRecursive(String[] items, int offset) {
    if (offset == items.length-1) {
      return getChildren(items[offset]);
    }
    XML[] matches = getChildren(items[offset]);
    XML[] outgoing = new XML[0];
    for (int i = 0; i < matches.length; i++) {
      XML[] kidMatches = matches[i].getChildrenRecursive(items, offset+1);
      outgoing = (XML[]) PApplet.concat(outgoing, kidMatches);
    }
    return outgoing;
  }


  /**
   * Appends a new child to the element. The child can be specified with either a
   * <b>String</b>, which will be used as the new tag's name, or as a reference to an
   * existing <b>XML</b> object.<br />
   * <br />
   * A reference to the newly created child is returned as an <b>XML</b> object.
   *
   * @webref xml:method
   * @webBrief Appends a new child to the element
   */
  public XML addChild(String tag) {
    Document document = node.getOwnerDocument();
    Node newChild = document.createElement(tag);
    return appendChild(newChild);
  }


  public XML addChild(XML child) {
    Document document = node.getOwnerDocument();
    Node newChild = document.importNode((Node) child.getNative(), true);
    return appendChild(newChild);
  }


  /** Internal handler to add the node structure. */
  protected XML appendChild(Node newNode) {
    node.appendChild(newNode);
    XML newbie = new XML(this, newNode);
    if (children != null) {
      children = (XML[]) PApplet.concat(children, new XML[] { newbie });
    }
    return newbie;
  }


  	/**
	 * Removes the specified element. First use <b>getChild()</b> to get a reference
	 * to the desired element. Then pass that reference to <b>removeChild()</b> to
	 * delete it.
	 *
	 * @webref xml:method
	 * @webBrief Removes the specified child
	 */
  public void removeChild(XML kid) {
    node.removeChild(kid.node);
    children = null;  // TODO not efficient
  }

  /**
   * Removes whitespace nodes.
   * Those whitespace nodes are required to reconstruct the original XML's spacing and indentation.
   * If you call this and use saveXML() your original spacing will be gone.
   * 
   * @nowebref
   * @webBrief Removes whitespace nodes
   */
  public void trim() {
    try {
      XPathFactory xpathFactory = XPathFactory.newInstance();
      XPathExpression xpathExp =
        xpathFactory.newXPath().compile("//text()[normalize-space(.) = '']");
      NodeList emptyTextNodes = (NodeList)
        xpathExp.evaluate(node, XPathConstants.NODESET);

      // Remove each empty text node from document.
      for (int i = 0; i < emptyTextNodes.getLength(); i++) {
        Node emptyTextNode = emptyTextNodes.item(i);
        emptyTextNode.getParentNode().removeChild(emptyTextNode);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


//  /** Remove whitespace nodes. */
//  public void trim() {
//////    public static boolean isWhitespace(XML xml) {
//////      if (xml.node.getNodeType() != Node.TEXT_NODE)
//////        return false;
//////      Matcher m = whitespace.matcher(xml.node.getNodeValue());
//////      return m.matches();
//////    }
////    trim(this);
////  }
//
//    checkChildren();
//    int index = 0;
//    for (int i = 0; i < children.length; i++) {
//      if (i != index) {
//        children[index] = children[i];
//      }
//      Node childNode = (Node) children[i].getNative();
//      if (childNode.getNodeType() != Node.TEXT_NODE ||
//          children[i].getContent().trim().length() > 0) {
//        children[i].trim();
//        index++;
//      }
//    }
//    if (index != children.length) {
//      children = (XML[]) PApplet.subset(children, 0, index);
//    }
//
//    // possibility, but would have to re-parse the object
//// helpdesk.objects.com.au/java/how-do-i-remove-whitespace-from-an-xml-document
////    TransformerFactory factory = TransformerFactory.newInstance();
////    Transformer transformer = factory.newTransformer(new StreamSource("strip-space.xsl"));
////    DOMSource source = new DOMSource(document);
////    StreamResult result = new StreamResult(System.out);
////    transformer.transform(source, result);
//
////    <xsl:stylesheet version="1.0"
////      xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
////   <xsl:output method="xml" omit-xml-declaration="yes"/>
////     <xsl:strip-space elements="*"/>
////     <xsl:template match="@*|node()">
////      <xsl:copy>
////       <xsl:apply-templates select="@*|node()"/>
////      </xsl:copy>
////     </xsl:template>
////   </xsl:stylesheet>
//  }


  /**
   * Counts the specified element's number of attributes, returned as an <b>int</b>.
   *
   * @webref xml:method
   * @webBrief Counts the specified element's number of attributes
   */
  public int getAttributeCount() {
    return node.getAttributes().getLength();
  }


  /**
   * Gets all of the specified element's attributes, and returns them as an array of <b>Strings</b>.
   *
   * @webref xml:method
   * @webBrief Returns a list of names of all attributes as an array
   */
  public String[] listAttributes() {
    NamedNodeMap nnm = node.getAttributes();
    String[] outgoing = new String[nnm.getLength()];
    for (int i = 0; i < outgoing.length; i++) {
      outgoing[i] = nnm.item(i).getNodeName();
    }
    return outgoing;
  }

  /**
   * Checks whether or not an element has the specified attribute.  The attribute 
   * must be specified as a <b>String</b>, and a <b>boolean</b> is returned.
   *
   * @webref xml:method
   * @webBrief Checks whether or not an element has the specified attribute
   */
  public boolean hasAttribute(String name) {
    return (node.getAttributes().getNamedItem(name) != null);
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name the non-null name of the attribute.
   * @return the value, or null if the attribute does not exist.
   */
//  public String getAttribute(String name) {
//    return this.getAttribute(name, null);
//  }


  /**
   * Returns the value of an attribute.
   *
   * @param name the non-null full name of the attribute.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
//  public String getAttribute(String name, String defaultValue) {
//    Node attr = node.getAttributes().getNamedItem(name);
//    return (attr == null) ? defaultValue : attr.getNodeValue();
//  }


  /**
   * Returns an attribute value of the element as a <b>String</b>. If the <b>defaultValue</b> 
   * parameter is specified and the attribute doesn't exist, then <b>defaultValue</b> 
   * is returned. If no <b>defaultValue</b> is specified and the attribute doesn't 
   * exist, <b>null</b> is returned.
   *
   * @webref xml:method
   * @webBrief Gets the content of an attribute as a <b>String</b>
   */
  public String getString(String name) {
    return getString(name, null);
  }


  public String getString(String name, String defaultValue) {
    NamedNodeMap attrs = node.getAttributes();
    if (attrs != null) {
      Node attr = attrs.getNamedItem(name);
      if (attr != null) {
        return attr.getNodeValue();
      }
    }
    return defaultValue;
  }


  /**
   * Sets the content of an element's attribute as a <b>String</b>.  The first <b>String</b> 
   * specifies the attribute name, while the second specifies the new content.
   *
   * @webref xml:method
   * @webBrief Sets the content of an attribute as a <b>String</b>
   */
  public void setString(String name, String value) {
    ((Element) node).setAttribute(name, value);
  }


  /**
   * Returns an attribute value of the element as an <b>int</b>. If the <b>defaultValue</b> 
   * parameter is specified and the attribute doesn't exist, then <b>defaultValue</b> 
   * is returned. If no <b>defaultValue</b> is specified and the attribute doesn't 
   * exist, the value 0 is returned.
   *
   * @webref xml:method
   * @webBrief Gets the content of an attribute as an <b>int</b>
   */
  public int getInt(String name) {
    return getInt(name, 0);
  }


  /**
   * Sets the content of an element's attribute as an <b>int</b>.  A <b>String</b> specifies 
   * the attribute name, while the int specifies the new content.
   *
   * @webref xml:method
   * @webBrief Sets the content of an attribute as an <b>int</b>
   */
  public void setInt(String name, int value) {
    setString(name, String.valueOf(value));
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name the non-null full name of the attribute
   * @param defaultValue the default value of the attribute
   * @return the value, or defaultValue if the attribute does not exist
   */
  public int getInt(String name, int defaultValue) {
    String value = getString(name);
    return (value == null) ? defaultValue : Integer.parseInt(value);
  }


  /**
   * Sets the content of an element as an <b>int</b>
   *
   * @nowebref
   */
  public void setLong(String name, long value) {
    setString(name, String.valueOf(value));
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name the non-null full name of the attribute.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  public long getLong(String name, long defaultValue) {
    String value = getString(name);
    return (value == null) ? defaultValue : Long.parseLong(value);
  }


  /**
   * Returns an attribute value of the element as a float. If the
   * <b>defaultValue</b> parameter is specified and the attribute doesn't exist,
   * then <b>defaultValue</b> is returned. If no <b>defaultValue</b> is specified
   * and the attribute doesn't exist, the value 0.0 is returned.
   *
   * @webref xml:method
   * @webBrief Gets the content of an attribute as a <b>float</b>
   */
  public float getFloat(String name) {
    return getFloat(name, 0);
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name the non-null full name of the attribute.
   * @param defaultValue the default value of the attribute.
   * @return the value, or defaultValue if the attribute does not exist.
   */
  public float getFloat(String name, float defaultValue) {
    String value = getString(name);
    return (value == null) ? defaultValue : Float.parseFloat(value);
  }


  /**
   * Sets the content of an element's attribute as a <b>float</b>.  A <b>String</b> specifies 
   * the attribute name, while the <b>float</b> specifies the new content.
   *
   * @webref xml:method
   * @webBrief Sets the content of an attribute as a <b>float</b>
   */
  public void setFloat(String name, float value) {
    setString(name, String.valueOf(value));
  }


  public double getDouble(String name) {
    return getDouble(name, 0);
  }


  /**
   * Returns the value of an attribute.
   *
   * @param name the non-null full name of the attribute
   * @param defaultValue the default value of the attribute
   * @return the value, or defaultValue if the attribute does not exist
   */
  public double getDouble(String name, double defaultValue) {
    String value = getString(name);
    return (value == null) ? defaultValue : Double.parseDouble(value);
  }


  public void setDouble(String name, double value) {
    setString(name, String.valueOf(value));
  }


  /**
   * Returns the content of an element. If there is no such content, 
   * <b>null</b> is returned.
   *
   * @webref xml:method
   * @webBrief Gets the content of an element
   * @return the content.
   * @see XML#getIntContent()
   * @see XML#getFloatContent()
   */
  public String getContent() {
    return node.getTextContent();
  }


  public String getContent(String defaultValue) {
    String s = node.getTextContent();
    return (s != null) ? s : defaultValue;
  }


  /**
   * Returns the content of an element as an <b>int</b>. If there is no such content, 
   * either <b>null</b> or the provided default value is returned.
   *
   * @webref xml:method
   * @webBrief Gets the content of an element as an <b>int</b>
   * @return the content.
   * @see XML#getContent()
   * @see XML#getFloatContent()
   */
  public int getIntContent() {
    return getIntContent(0);
  }


  /**
   * @param defaultValue the default value of the attribute
   */
  public int getIntContent(int defaultValue) {
    return PApplet.parseInt(node.getTextContent(), defaultValue);
  }


  /**
   * Returns the content of an element as a <b>float</b>. If there is no such content, 
   * either <b>null</b> or the provided default value is returned.
   *
   * @webref xml:method
   * @webBrief Gets the content of an element as a <b>float</b>
   * @return the content.
   * @see XML#getContent()
   * @see XML#getIntContent()
   */
  public float getFloatContent() {
    return getFloatContent(0);
  }


  /**
   * @param defaultValue the default value of the attribute
   */
  public float getFloatContent(float defaultValue) {
    return PApplet.parseFloat(node.getTextContent(), defaultValue);
  }


  public long getLongContent() {
    return getLongContent(0);
  }


  public long getLongContent(long defaultValue) {
    String c = node.getTextContent();
    if (c != null) {
      try {
        return Long.parseLong(c);
      } catch (NumberFormatException nfe) { }
    }
    return defaultValue;
  }


  public double getDoubleContent() {
    return getDoubleContent(0);
  }


  public double getDoubleContent(double defaultValue) {
    String c = node.getTextContent();
    if (c != null) {
      try {
        return Double.parseDouble(c);
      } catch (NumberFormatException nfe) { }
    }
    return defaultValue;
  }


  /**
   * Sets the element's content, which is specified as a <b>String</b>.
   *
   * @webref xml:method
   * @webBrief Sets the content of an element
   */
  public void setContent(String text) {
    node.setTextContent(text);
  }


  public void setIntContent(int value) {
    setContent(String.valueOf(value));
  }


  public void setFloatContent(float value) {
    setContent(String.valueOf(value));
  }


  public void setLongContent(long value) {
    setContent(String.valueOf(value));
  }


  public void setDoubleContent(double value) {
    setContent(String.valueOf(value));
  }


  /**
   * Takes an <b>XML</b> object and converts it to a <b>String</b>, formatting its content as
   * specified with the <b>indent</b> parameter.<br />
   * <br />
   * If indent is set to -1, then the String is returned with no line breaks, no
   * indentation, and no <b>XML</b> declaration.<br />
   * <br />
   * If indent is set to 0 or greater, then the <b>String</b> is returned with line
   * breaks, and the specified number of spaces as indent values. Meaning, there
   * will be no indentation if 0 is specified, or each indent will be replaced
   * with the corresponding number of spaces: 1, 2, 3, and so on.
   *
   * @webref xml:method
   * @webBrief Formats <b>XML</b> data as a <b>String</b>
   * @param indent -1 for a single line (and no declaration), >= 0 for indents and
   *               newlines
   * @return the content
   * @see XML#toString()
   */
  public String format(int indent) {
    try {
      // entities = doctype.getEntities()
      boolean useIndentAmount = false;
      TransformerFactory factory = TransformerFactory.newInstance();
      if (indent != -1) {
        try {
          factory.setAttribute("indent-number", indent);
        } catch (IllegalArgumentException e) {
          useIndentAmount = true;
        }
      }
      Transformer transformer = factory.newTransformer();

      // Add the XML declaration at the top if this node is the root and we're
      // not writing to a single line (indent = -1 means single line).
      if (indent == -1 || parent == null) {
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      } else {
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      }

//      transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "sample.dtd");

      transformer.setOutputProperty(OutputKeys.METHOD, "xml");

//      transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "yes");  // huh?

//      transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
//          "-//W3C//DTD XHTML 1.0 Transitional//EN");
//      transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
//          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd");

      // For Android, because (at least 2.3.3) doesn't like indent-number
      if (useIndentAmount) {
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
      }

//      transformer.setOutputProperty(OutputKeys.ENCODING,"ISO-8859-1");
//      transformer.setOutputProperty(OutputKeys.ENCODING,"UTF8");
      transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
//      transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS

      // Always indent, otherwise the XML declaration will just be jammed
      // onto the first line with the XML code as well.
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");

//      Properties p = transformer.getOutputProperties();
//      for (Object key : p.keySet()) {
//        System.out.println(key + " -> " + p.get(key));
//      }

      // If you smell something, that's because this code stinks. No matter
      // the settings of the Transformer object, if the XML document already
      // has whitespace elements, it won't bother re-indenting/re-formatting.
      // So instead, transform the data once into a single line string.
      // If indent is -1, then we're done. Otherwise re-run and the settings
      // of the factory will kick in. If you know a better way to do this,
      // please contribute. I've wasted too much of my Sunday on it. But at
      // least the Giants are getting blown out by the Falcons.

      final String decl = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
      final String sep = System.getProperty("line.separator");

      StringWriter tempWriter = new StringWriter();
      StreamResult tempResult = new StreamResult(tempWriter);
      transformer.transform(new DOMSource(node), tempResult);
      String[] tempLines = PApplet.split(tempWriter.toString(), sep);
//      PApplet.println(tempLines);
      if (tempLines[0].startsWith("<?xml")) {
        // Remove XML declaration from the top before slamming into one line
        int declEnd = tempLines[0].indexOf("?>") + 2;
        //if (tempLines[0].length() == decl.length()) {
        if (tempLines[0].length() == declEnd) {
          // If it's all the XML declaration, remove it
//          PApplet.println("removing first line");
          tempLines = PApplet.subset(tempLines, 1);
        } else {
//          PApplet.println("removing part of first line");
          // If the first node has been moved to this line, be more careful
          //tempLines[0] = tempLines[0].substring(decl.length());
          tempLines[0] = tempLines[0].substring(declEnd);
        }
      }
      String singleLine = PApplet.join(PApplet.trim(tempLines), "");
      if (indent == -1) {
        return singleLine;
      }

      // Might just be whitespace, which won't be valid XML for parsing below.
      // https://github.com/processing/processing/issues/1796
      // Since indent is not -1, that means they want valid XML,
      // so we'll give them the single line plus the decl... Lame? sure.
      if (singleLine.trim().length() == 0) {
        // You want whitespace? I've got your whitespace right here.
        return decl + sep + singleLine;
      }

      // Since the indent is not -1, bring back the XML declaration
      //transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

      StringWriter stringWriter = new StringWriter();
      StreamResult xmlOutput = new StreamResult(stringWriter);
//      DOMSource source = new DOMSource(node);
      Source source = new StreamSource(new StringReader(singleLine));
      transformer.transform(source, xmlOutput);
      String outgoing = stringWriter.toString();

      // Add the XML declaration to the top if it's not there already
      if (outgoing.startsWith(decl)) {
        int declen = decl.length();
        int seplen = sep.length();
        if (outgoing.length() > declen + seplen &&
            !outgoing.substring(declen, declen + seplen).equals(sep)) {
          // make sure there's a line break between the XML decl and the code
          return outgoing.substring(0, decl.length()) +
            sep + outgoing.substring(decl.length());
        }
        return outgoing;
      } else {
        return decl + sep + outgoing;
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }


  public void print() {
    PApplet.println(format(2));
  }


  /**
   * Takes an <b>XML</b> object and converts it to a <b>String</b>, using default formatting
   * rules (includes an XML declaration, line breaks, and two spaces for indents).
   * These are the same formatting rules used by <b>println()</b> when printing an
   * <b>XML</b> object. This method produces the same results as using <b>format(2)</b>.
   *
   * @webref xml:method
   * @webBrief Gets <b>XML</b> data as a <b>String</b> using default formatting
   * @return the content
   * @see XML#format(int)
   */
  @Override
  public String toString() {
    //return format(2);
    return format(-1);
  }
}
