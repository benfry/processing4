package processing.data;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TableTest {

    class Person {
        public String name;
        public int age;

        public Person() {
            name = "";
            age = -1;
        }
    }


    Person[] people;

    @Test
    public void parseInto() {
        Table table = new Table();
        table.addColumn("name");
        table.addColumn("age");

        TableRow row = table.addRow();
        row.setString("name", "Person1");
        row.setInt("age", 30);

        table.parseInto(this, "people");

        Assert.assertEquals(people[0].name, "Person1");
        Assert.assertEquals(people[0].age, 30);
    }
}
