package processing.data;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import processing.core.PApplet;

/**
 * Helper class for a list of <b>String</b> objects. Lists are designed
 * to have some features of <b>ArrayList</b>, but to maintain the
 * simplicity and efficiency of working with arrays.
 * Functions such as <b>sort()</b> and <b>shuffle()</b> always act on
 * the list itself. To get a sorted copy, use <b>list.copy().sort()</b>.
 *
 * @webref data:composite
 * @webBrief Helper class for a list of Strings
 * @see IntList
 * @see FloatList
 */
public class StringList implements Iterable<String> {
  int count;
  String[] data;


  public StringList() {
    this(10);
  }

  /**
   * @nowebref
   */
  public StringList(int length) {
    data = new String[length];
  }

  /**
   * @nowebref
   */
  public StringList(String[] list) {
    count = list.length;
    data = new String[count];
    System.arraycopy(list, 0, data, 0, count);
  }


  /**
   * Construct a StringList from a random pile of objects. Null values will
   * stay null, but all the others will be converted to String values.
   *
   * @nowebref
   */
  public StringList(Object... items) {
    count = items.length;
    data = new String[count];
    int index = 0;
    for (Object o : items) {
      // Keep null values null (because join() will make non-null anyway)
      if (o != null) {  // leave null values null
        data[index] = o.toString();
      }
      index++;
    }
  }


  /**
   * Create from something iterable, for instance:
   * StringList list = new StringList(hashMap.keySet());
   *
   * @nowebref
   */
  public StringList(Iterable<String> iterable) {
    this(10);
    for (String s : iterable) {
      append(s);
    }
  }


  /**
   * Improve efficiency by removing allocated but unused entries from the
   * internal array used to store the data. Set to private, though it could
   * be useful to have this public if lists are frequently making drastic
   * size changes (from very large to very small).
   */
  private void crop() {
    if (count != data.length) {
      data = PApplet.subset(data, 0, count);
    }
  }


  /**
   * Get the length of the list.
   *
   * @webref stringlist:method
   * @webBrief Get the length of the list
   */
  public int size() {
    return count;
  }


  public void resize(int length) {
    if (length > data.length) {
      String[] temp = new String[length];
      System.arraycopy(data, 0, temp, 0, count);
      data = temp;

    } else if (length > count) {
      Arrays.fill(data, count, length, null);
    }
    count = length;
  }


  /**
   * Remove all entries from the list.
   *
   * @webref stringlist:method
   * @webBrief Remove all entries from the list
   */
  public void clear() {
    count = 0;
  }


  /**
   * Get an entry at a particular index.
   *
   * @webref stringlist:method
   * @webBrief Get an entry at a particular index
   */
  public String get(int index) {
    if (index >= count) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    return data[index];
  }


  /**
   * Set the entry at a particular index. If the index is past the length of
   * the list, it'll expand the list to accommodate, and fill the intermediate
   * entries with <b>null</b>.
   *
   * @webref stringlist:method
   * @webBrief Set an entry at a particular index
   */
  public void set(int index, String what) {
    if (index >= count) {
      data = PApplet.expand(data, index+1);
      for (int i = count; i < index; i++) {
        data[i] = null;
      }
      count = index+1;
    }
    data[index] = what;
  }


  /** Just an alias for append(), but matches pop() */
  public void push(String value) {
    append(value);
  }


  public String pop() {
    if (count == 0) {
      throw new RuntimeException("Can't call pop() on an empty list");
    }
    String value = get(count-1);
    data[--count] = null;  // avoid leak
    return value;
  }


  /**
   * Remove an element from the specified index.
   *
   * @webref stringlist:method
   * @webBrief Remove an element from the specified index
   */
  public String remove(int index) {
    if (index < 0 || index >= count) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    String entry = data[index];
    for (int i = index; i < count-1; i++) {
      data[i] = data[i+1];
    }
    count--;
    return entry;
  }


  // Remove the first instance of a particular value and return its index.
  @SuppressWarnings("unused")
  public int removeValue(String value) {
    if (value == null) {
      for (int i = 0; i < count; i++) {
        if (data[i] == null) {
          remove(i);
          return i;
        }
      }
    } else {
      int index = index(value);
      if (index != -1) {
        remove(index);
        return index;
      }
    }
    return -1;
  }


  // Remove all instances of a particular value and return the count removed.
  @SuppressWarnings("unused")
  public int removeValues(String value) {
    int ii = 0;
    if (value == null) {
      for (int i = 0; i < count; i++) {
        if (data[i] != null) {
          data[ii++] = data[i];
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (!value.equals(data[i])) {
          data[ii++] = data[i];
        }
      }
    }
    int removed = count - ii;
    count = ii;
    return removed;
  }


  // replace the first value that matches, return the index that was replaced
  @SuppressWarnings("unused")
  public int replaceValue(String value, String newValue) {
    if (value == null) {
      for (int i = 0; i < count; i++) {
        if (data[i] == null) {
          data[i] = newValue;
          return i;
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (value.equals(data[i])) {
          data[i] = newValue;
          return i;
        }
      }
    }
    return -1;
  }


  // replace all values that match, return the count of those replaced
  @SuppressWarnings("unused")
  public int replaceValues(String value, String newValue) {
    int changed = 0;
    if (value == null) {
      for (int i = 0; i < count; i++) {
        if (data[i] == null) {
          data[i] = newValue;
          changed++;
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (value.equals(data[i])) {
          data[i] = newValue;
          changed++;
        }
      }
    }
    return changed;
  }


  /**
   * Add a new entry to the list.
   *
   * @webref stringlist:method
   * @webBrief Add a new entry to the list
   */
  public void append(String value) {
    if (count == data.length) {
      data = PApplet.expand(data);
    }
    data[count++] = value;
  }


  public void append(String[] values) {
    for (String v : values) {
      append(v);
    }
  }


  public void append(StringList list) {
    for (String v : list.values()) {  // will concat the list...
      append(v);
    }
  }


  /** Add this value, but only if it's not already in the list. */
  public void appendUnique(String value) {
    if (!hasValue(value)) {
      append(value);
    }
  }


//  public void insert(int index, int value) {
//    if (index+1 > count) {
//      if (index+1 < data.length) {
//    }
//  }
//    if (index >= data.length) {
//      data = PApplet.expand(data, index+1);
//      data[index] = value;
//      count = index+1;
//
//    } else if (count == data.length) {
//    if (index >= count) {
//      //int[] temp = new int[count << 1];
//      System.arraycopy(data, 0, temp, 0, index);
//      temp[index] = value;
//      System.arraycopy(data, index, temp, index+1, count - index);
//      data = temp;
//
//    } else {
//      // data[] has room to grow
//      // for() loop believed to be faster than System.arraycopy over itself
//      for (int i = count; i > index; --i) {
//        data[i] = data[i-1];
//      }
//      data[index] = value;
//      count++;
//    }
//  }


  public void insert(int index, String value) {
    insert(index, new String[] { value });
  }


  // same as splice
  public void insert(int index, String[] values) {
    if (index < 0) {
      throw new IllegalArgumentException("insert() index cannot be negative: it was " + index);
    }
    if (index >= data.length) {
      throw new IllegalArgumentException("insert() index " + index + " is past the end of this list");
    }

    String[] temp = new String[count + values.length];

    // Copy the old values, but not more than already exist
    System.arraycopy(data, 0, temp, 0, Math.min(count, index));

    // Copy the new values into the proper place
    System.arraycopy(values, 0, temp, index, values.length);

//    if (index < count) {
    // The index was inside count, so it's a true splice/insert
    System.arraycopy(data, index, temp, index+values.length, count - index);
    count = count + values.length;
//    } else {
//      // The index was past 'count', so the new count is weirder
//      count = index + values.length;
//    }
    data = temp;
  }


  public void insert(int index, StringList list) {
    insert(index, list.values());
  }


    // below are aborted attempts at more optimized versions of the code
    // that are harder to read and debug...

//    if (index + values.length >= count) {
//      // We're past the current 'count', check to see if we're still allocated
//      // index 9, data.length = 10, values.length = 1
//      if (index + values.length < data.length) {
//        // There's still room for these entries, even though it's past 'count'.
//        // First clear out the entries leading up to it, however.
//        for (int i = count; i < index; i++) {
//          data[i] = 0;
//        }
//        data[index] =
//      }
//      if (index >= data.length) {
//        int length = index + values.length;
//        int[] temp = new int[length];
//        System.arraycopy(data, 0, temp, 0, count);
//        System.arraycopy(values, 0, temp, index, values.length);
//        data = temp;
//        count = data.length;
//      } else {
//
//      }
//
//    } else if (count == data.length) {
//      int[] temp = new int[count << 1];
//      System.arraycopy(data, 0, temp, 0, index);
//      temp[index] = value;
//      System.arraycopy(data, index, temp, index+1, count - index);
//      data = temp;
//
//    } else {
//      // data[] has room to grow
//      // for() loop believed to be faster than System.arraycopy over itself
//      for (int i = count; i > index; --i) {
//        data[i] = data[i-1];
//      }
//      data[index] = value;
//      count++;
//    }


  /** Return the first index of a particular value. */
  public int index(String what) {
    if (what == null) {
      for (int i = 0; i < count; i++) {
        if (data[i] == null) {
          return i;
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (what.equals(data[i])) {
          return i;
        }
      }
    }
    return -1;
  }


  /**
   * Check if a value is a part of the list
   *
   * @webref stringlist:method
   * @webBrief Check if a value is a part of the list
   */
  public boolean hasValue(String value) {
    if (value == null) {
      for (int i = 0; i < count; i++) {
        if (data[i] == null) {
          return true;
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (value.equals(data[i])) {
          return true;
        }
      }
    }
    return false;
  }


  /**
   * Sorts the array in place.
   *
   * @webref stringlist:method
   * @webBrief Sorts the array in place
   */
  public void sort() {
    sortImpl(false);
  }


  /**
   * A sort in reverse. It's equivalent to running <b>sort()</b> and then 
   * <b>reverse()</b>, but is more efficient than running each separately.
   *
   * @webref stringlist:method
   * @webBrief A sort in reverse
   */
  public void sortReverse() {
    sortImpl(true);
  }


  private void sortImpl(final boolean reverse) {
    new Sort() {
      @Override
      public int size() {
        return count;
      }

      @Override
      public int compare(int a, int b) {
        int diff = data[a].compareToIgnoreCase(data[b]);
        return reverse ? -diff : diff;
      }

      @Override
      public void swap(int a, int b) {
        String temp = data[a];
        data[a] = data[b];
        data[b] = temp;
      }
    }.run();
  }


  /**
   * Reverse the order of the list
   *
   * @webref stringlist:method
   * @webBrief Reverse the order of the list
   */
  public void reverse() {
    int ii = count - 1;
    for (int i = 0; i < count/2; i++) {
      String t = data[i];
      data[i] = data[ii];
      data[ii] = t;
      --ii;
    }
  }


  /**
   * Randomize the order of the list elements. 
   *
   * @webref stringlist:method
   * @webBrief Randomize the order of the list elements
   */
  @SuppressWarnings("unused")
  public void shuffle() {
    Random r = new Random();
    int num = count;
    while (num > 1) {
      int value = r.nextInt(num);
      num--;
      String temp = data[num];
      data[num] = data[value];
      data[value] = temp;
    }
  }


  /**
   * Randomize the list order using the random() function from the specified
   * sketch, allowing shuffle() to use its current randomSeed() setting.
   */
  @SuppressWarnings("unused")
  public void shuffle(PApplet sketch) {
    int num = count;
    while (num > 1) {
      int value = (int) sketch.random(num);
      num--;
      String temp = data[num];
      data[num] = data[value];
      data[value] = temp;
    }
  }


  /**
   * Return a random value from the list. Throws an exception if there are no
   * entries available. (Can't just return null because IntList and FloatList
   * can't do that, and would be inconsistent.)
   */
  public String choice() {
    if (count == 0) {
      throw new ArrayIndexOutOfBoundsException("No entries in this StringList");
    }
    return data[(int) (Math.random() * count)];
  }


  // removing in 4.0.2, seems like overkill... if something this specific is
  // needed, then better to use a more elaborate/pedantic setup anyway.
//  /**
//   * Return a random value from the list, using the
//   * randomSeed() from the specified sketch object.
//   */
//  public String choice(PApplet sketch) {
//    if (count == 0) {
//      throw new ArrayIndexOutOfBoundsException("No entries in this StringList");
//    }
//    return data[(int) sketch.random(count)];
//  }


  public String removeChoice() {
    if (count == 0) {
      throw new ArrayIndexOutOfBoundsException("No entries in this StringList");
    }
    int index = (int) (Math.random() * count);
    return remove(index);
  }


  /**
   * Make the entire list lower case.
   *
   * @webref stringlist:method
   * @webBrief Make the entire list lower case
   */
  public void lower() {
    for (int i = 0; i < count; i++) {
      if (data[i] != null) {
        data[i] = data[i].toLowerCase();
      }
    }
  }


  /**
   * Make the entire list upper case.
   *
   * @webref stringlist:method
   * @webBrief Make the entire list upper case
   */
  public void upper() {
    for (int i = 0; i < count; i++) {
      if (data[i] != null) {
        data[i] = data[i].toUpperCase();
      }
    }
  }


  public StringList copy() {
    StringList outgoing = new StringList(data);
    outgoing.count = count;
    return outgoing;
  }


  /**
   * Returns the actual array being used to store the data. Suitable for
   * iterating with a for() loop, but modifying the list could cause terrible
   * things to happen.
   */
  public String[] values() {
    crop();
    return data;
  }


  @Override
  public Iterator<String> iterator() {
    return new Iterator<>() {
      int index = -1;

      public void remove() {
        StringList.this.remove(index);
        index--;
      }

      public String next() {
        return data[++index];
      }

      public boolean hasNext() {
        return index+1 < count;
      }
    };
  }


  @Deprecated
  public String[] array() {
    return toArray();
  }


  /**
   * Create a new array with a copy of all the values.
   *
   * @return an array sized by the length of the list with each of the values.
   * @webref stringlist:method
   * @webBrief Create a new array with a copy of all the values
   */
  public String[] toArray() {
    return toArray(null);
  }


  @Deprecated
  public String[] array(String[] array) {
    return toArray(array);
  }


  /**
   * Copy values into the specified array. If the specified array is null or
   * not the same size, a new array will be allocated.
   */
  public String[] toArray(String[] array) {
    if (array == null || array.length != count) {
      array = new String[count];
    }
    System.arraycopy(data, 0, array, 0, count);
    return array;
  }


  @SuppressWarnings("unused")
  public StringList getSubset(int start) {
    return getSubset(start, count - start);
  }


  public StringList getSubset(int start, int num) {
    String[] subset = new String[num];
    System.arraycopy(data, start, subset, 0, num);
    return new StringList(subset);
  }


  /** Get a list of all unique entries. */
  public String[] getUnique() {
    return getTally().keyArray();
  }


  /** Count the number of times each String entry is found in this list. */
  public IntDict getTally() {
    IntDict outgoing = new IntDict();
    for (int i = 0; i < count; i++) {
      outgoing.increment(data[i]);
    }
    return outgoing;
  }


  /** Create a dictionary associating each entry in this list to its index. */
  public IntDict getOrder() {
    IntDict outgoing = new IntDict();
    for (int i = 0; i < count; i++) {
      outgoing.set(data[i], i);
    }
    return outgoing;
  }


  public String join(String separator) {
    if (count == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(data[0]);
    for (int i = 1; i < count; i++) {
      sb.append(separator);
      sb.append(data[i]);
    }
    return sb.toString();
  }


  public void print() {
    for (int i = 0; i < count; i++) {
      System.out.format("[%d] %s%n", i, data[i]);
    }
  }


  /**
   * Save tab-delimited entries to a file (TSV format, UTF-8 encoding)
   */
  public void save(File file) {
    PrintWriter writer = PApplet.createWriter(file);
    write(writer);
    writer.close();
  }


  /**
   * Write entries to a PrintWriter, one per line
   */
  public void write(PrintWriter writer) {
    for (int i = 0; i < count; i++) {
      writer.println(data[i]);
    }
    writer.flush();
  }


  /**
   * Return this dictionary as a String in JSON format.
   */
  public String toJSON() {
    StringList temp = new StringList();
    for (String item : this) {
      temp.append(JSONObject.quote(item));
    }
    return "[ " + temp.join(", ") + " ]";
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " size=" + size() + " " + toJSON();
  }
}
