package processing.mode.java;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class ImportStatementTest {

    private ImportStatement wholePackage;
    private ImportStatement singleClass;
    private ImportStatement staticNoWildcard;
    private ImportStatement staticWildcard;

    @Before
    public void setUp() {
        wholePackage = ImportStatement.parse("java.util.*");
        singleClass = ImportStatement.parse("java.util.List");
        staticNoWildcard = ImportStatement.parse("static org.processing.test.Test.init");
        staticWildcard = ImportStatement.parse("static org.processing.test.Test.*");
    }

    @Test
    public void testWholePackageShortcut() {
        Assert.assertTrue(wholePackage.isSameAs(ImportStatement.wholePackage("java.util")));
    }

    @Test
    public void testSingleClassShortcut() {
        Assert.assertTrue(singleClass.isSameAs(ImportStatement.singleClass("java.util.List")));
    }

    @Test
    public void testGetFullSourceLine() {
        Assert.assertEquals(
                wholePackage.getFullSourceLine(),
                "import java.util.*;"
        );

        Assert.assertEquals(
                singleClass.getFullSourceLine(),
                "import java.util.List;"
        );

        Assert.assertEquals(
                staticNoWildcard.getFullSourceLine(),
                "import static org.processing.test.Test.init;"
        );

        Assert.assertEquals(
                staticWildcard.getFullSourceLine(),
                "import static org.processing.test.Test.*;"
        );
    }

    @Test
    public void testGetFullMemberName() {
        Assert.assertEquals(
                wholePackage.getFullMemberName(),
                "java.util.*"
        );

        Assert.assertEquals(
                singleClass.getFullMemberName(),
                "java.util.List"
        );

        Assert. assertEquals(
                staticNoWildcard.getFullMemberName(),
                "org.processing.test.Test.init"
        );

        Assert.assertEquals(
                staticWildcard.getFullMemberName(),
                "org.processing.test.Test.*"
        );
    }

    @Test
    public void testGetMemberName() {
        Assert.assertEquals(
                wholePackage.getMemberName(),
                "*"
        );

        Assert.assertEquals(
                singleClass.getMemberName(),
                "List"
        );

        Assert. assertEquals(
                staticNoWildcard.getMemberName(),
                "Test.init"
        );

        Assert.assertEquals(
                staticWildcard.getMemberName(),
                "Test.*"
        );
    }

    @Test
    public void testGetPackageName() {
        Assert.assertEquals(
                wholePackage.getPackageName(),
                "java.util"
        );

        Assert.assertEquals(
                singleClass.getPackageName(),
                "java.util"
        );

        Assert. assertEquals(
                staticNoWildcard.getPackageName(),
                "org.processing.test"
        );

        Assert.assertEquals(
                staticWildcard.getPackageName(),
                "org.processing.test"
        );
    }

    @Test
    public void testIsStarredImport() {
        Assert.assertTrue(wholePackage.isStarredImport());
        Assert.assertFalse(singleClass.isStarredImport());
        Assert.assertFalse(staticNoWildcard.isStarredImport());
        Assert.assertTrue(staticWildcard.isStarredImport());
    }

    @Test
    public void testIsStaticImport() {
        Assert.assertFalse(wholePackage.isStaticImport());
        Assert.assertFalse(singleClass.isStaticImport());
        Assert.assertTrue(staticNoWildcard.isStaticImport());
        Assert.assertTrue(staticWildcard.isStaticImport());
    }

    @Test
    public void testIsSameAs() {
        Assert.assertTrue(wholePackage.isSameAs(ImportStatement.parse("java.util.*")));
        Assert.assertFalse(wholePackage.isSameAs(ImportStatement.parse("java.other.*")));
    }

}
