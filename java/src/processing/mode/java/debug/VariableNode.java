/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  version 2, as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.java.debug;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

/**
 * Model for a variable in the variable inspector. Has a type and name and
 * optionally a value. Can have sub-variables (as is the case for objects,
 * and arrays).
 */
public class VariableNode implements MutableTreeNode {
  public static final int TYPE_UNKNOWN = -1;
  public static final int TYPE_OBJECT = 0;
  public static final int TYPE_ARRAY = 1;
  public static final int TYPE_INTEGER = 2;
  public static final int TYPE_FLOAT = 3;
  public static final int TYPE_BOOLEAN = 4;
  public static final int TYPE_CHAR = 5;
  public static final int TYPE_STRING = 6;
  public static final int TYPE_LONG = 7;
  public static final int TYPE_DOUBLE = 8;
  public static final int TYPE_BYTE = 9;
  public static final int TYPE_SHORT = 10;
  public static final int TYPE_VOID = 11;

  private static final Pattern ARRAY_REGEX = Pattern.compile("^(?<prefix>[^\\[]+)(?<unbounded>(\\[\\])+)(?<bounded>(\\[\\d+\\])+)(?<unneeded>[^\\[]*)$");

  protected String type;
  protected String name;
  protected Value value;
  protected List<MutableTreeNode> children = new ArrayList<>();
  protected MutableTreeNode parent;

  /**
   * Construct a {@link VariableNode}.
   * @param name the name
   * @param type the type
   * @param value the value
   */
  public VariableNode(String name, String type, Value value) {
    this.name = name;
    this.type = type;
    this.value = value;
  }


  public void setValue(Value value) {
    this.value = value;
  }


  public Value getValue() {
    return value;
  }


  /**
   * Get a String representation of this variable nodes value.
   *
   * @return a String representing the value.
   */
  public String getStringValue() {
    if (value == null) {
      return "null";
    }

    int typeDescriptor = getType();
    if (typeDescriptor == TYPE_OBJECT) {
      return "instance of " + type;
    } else if (typeDescriptor == TYPE_ARRAY) {
      return describeArray(value.toString());
    } else if (typeDescriptor == TYPE_STRING) {
      // use original string value (without quotes)
      return ((StringReference) value).value();
    } else {
      return value.toString();
    }
  }

  public String getTypeName() {
    return type;
  }


  public int getType() {
    if (type == null) {
      return TYPE_UNKNOWN;
    }
    if (type.endsWith("[]")) {
      return TYPE_ARRAY;
    }
    if (type.equals("int")) {
      return TYPE_INTEGER;
    }
    if (type.equals("long")) {
      return TYPE_LONG;
    }
    if (type.equals("byte")) {
      return TYPE_BYTE;
    }
    if (type.equals("short")) {
      return TYPE_SHORT;
    }
    if (type.equals("float")) {
      return TYPE_FLOAT;
    }
    if (type.equals("double")) {
      return TYPE_DOUBLE;
    }
    if (type.equals("char")) {
      return TYPE_CHAR;
    }
    if (type.equals("java.lang.String")) {
      return TYPE_STRING;
    }
    if (type.equals("boolean")) {
      return TYPE_BOOLEAN;
    }
    if (type.equals("void")) {
      return TYPE_VOID; //TODO: check if this is correct
    }
    return TYPE_OBJECT;
  }


  public String getName() {
    return name;
  }


  public void setName(String name) {
    this.name = name;
  }


  /**
   * Add a {@link VariableNode} as child.
   *
   * @param c the {@link VariableNode} to add.
   */
  public void addChild(VariableNode c) {
    children.add(c);
    c.setParent(this);
  }


  /**
   * Add multiple {@link VariableNode}s as children.
   *
   * @param children the list of {@link VariableNode}s to add.
   */
  public void addChildren(List<VariableNode> children) {
    for (VariableNode child : children) {
      addChild(child);
    }
  }


  @Override
  public TreeNode getChildAt(int i) {
    return children.get(i);
  }


  @Override
  public int getChildCount() {
    return children.size();
  }


  @Override
  public TreeNode getParent() {
    return parent;
  }


  @Override
  public int getIndex(TreeNode tn) {
    return children.indexOf(tn);
  }


  @Override
  public boolean getAllowsChildren() {
    if (value == null) {
      return false;
    }

    // handle strings
    if (getType() == TYPE_STRING) {
      return false;
    }

    // handle arrays
    if (getType() == TYPE_ARRAY) {
      ArrayReference array = (ArrayReference) value;
      return array.length() > 0;
    }
    // handle objects
    if (getType() == TYPE_OBJECT) { // this also rules out null
      // check if this object has any fields
      ObjectReference obj = (ObjectReference) value;
      return !obj.referenceType().visibleFields().isEmpty();
    }

    return false;
  }


  /**
   * This controls the default icon and disclosure triangle.
   *
   * @return true, will show "folder" icon and disclosure triangle.
   */
  @Override
  public boolean isLeaf() {
    //return children.size() == 0;
    return !getAllowsChildren();
  }


  @Override
  public Enumeration<MutableTreeNode> children() {
    return Collections.enumeration(children);
  }


  /**
   * Get a String representation of this {@link VariableNode}.
   *
   * @return the name of the variable (for sorting to work).
   */
  @Override
  public String toString() {
    return getName(); // for sorting
  }


  /**
   * Get a String description of this {@link VariableNode}. Contains the type,
   * name and value.
   *
   * @return the description
   */
  public String getDescription() {
    String str = "";
    if (type != null) {
      str += type + " ";
    }
    str += name;
    str += " = " + getStringValue();
    return str;
  }


  @Override
  public void insert(MutableTreeNode mtn, int i) {
    children.add(i, this);
  }


  @Override
  public void remove(int i) {
    MutableTreeNode mtn = children.remove(i);
    if (mtn != null) {
      mtn.setParent(null);
    }
  }


  @Override
  public void remove(MutableTreeNode mtn) {
    children.remove(mtn);
    mtn.setParent(null);
  }


  /**
   * Remove all children from this {@link VariableNode}.
   */
  public void removeAllChildren() {
    for (MutableTreeNode mtn : children) {
      mtn.setParent(null);
    }
    children.clear();
  }


  @Override
  public void setUserObject(Object o) {
    if (o instanceof Value) {
      value = (Value) o;
    }
  }


  @Override
  public void removeFromParent() {
    parent.remove(this);
    this.parent = null;
  }


  @Override
  public void setParent(MutableTreeNode mtn) {
    parent = mtn;
  }


  /**
   * Test for equality. To be equal, two {@link VariableNode}s need to have
   * equal type, name and value.
   *
   * @param obj the object to test for equality with this {@link VariableNode}
   * @return true if the given object is equal to this {@link VariableNode}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final VariableNode other = (VariableNode) obj;
    if ((this.type == null) ? (other.type != null) : !this.type.equals(other.type)) {
      //System.out.println("type not equal");
      return false;
    }
    if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
      //System.out.println("name not equal");
      return false;
    }
    if (this.value != other.value && (this.value == null || !this.value.equals(other.value))) {
      //System.out.println("value not equal");
      return false;
    }
    return true;
  }


  /**
   * Returns a hash code based on type, name and value.
   */
  @Override
  public int hashCode() {
    int hash = 3;
    hash = 97 * hash + (this.type != null ? this.type.hashCode() : 0);
    hash = 97 * hash + (this.name != null ? this.name.hashCode() : 0);
    hash = 97 * hash + (this.value != null ? this.value.hashCode() : 0);
    return hash;
  }


  /**
   * Describe an array in a human friendly description.
   * 
   * @see Issue #606
   * @param fullDescription The full description of the array like "instance of
   *    int[5] (id=998)" or "instance of int[][5] (id=998)"
   * @return Human-friendly description like "instance of int[5]" or
   *    "instance of int[5][]".
   */
  private String describeArray(String fullDescription) {
    Matcher matcher = ARRAY_REGEX.matcher(fullDescription);
    if (!matcher.matches()) {
      return fullDescription;
    }
    
    StringJoiner joiner = new StringJoiner("");
    joiner.add(matcher.group("prefix")); // Type without brackets
    joiner.add(matcher.group("bounded")); // Brackets with numbers
    joiner.add(matcher.group("unbounded")); // Brackets without numbers
    return joiner.toString();
  }
}
