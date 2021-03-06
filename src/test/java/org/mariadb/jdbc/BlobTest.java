package org.mariadb.jdbc;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.sql.*;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class BlobTest extends BaseTest {
    /**
     * Initialisation.
     *
     * @throws SQLException exception
     */
    @BeforeClass()
    public static void initClass() throws SQLException {
        createTable("bug716378", "id int not null primary key auto_increment, test longblob, test2 blob, test3 text");
        createTable("BlobTeststreamtest2", "id int primary key not null, st varchar(20), strm text", "CHARSET utf8");
        createTable("BlobTeststreamtest3", "id int primary key not null, strm text", "CHARSET utf8");
        createTable("BlobTestclobtest", "id int not null primary key, strm text", "CHARSET utf8");
        createTable("BlobTestclobtest2", "strm text", "CHARSET utf8");
        createTable("BlobTestclobtest3", "id int not null primary key, strm text", "CHARSET utf8");
        createTable("BlobTestclobtest4", "id int not null primary key, strm text", "CHARSET utf8");
        createTable("BlobTestclobtest5", "id int not null primary key, strm text", "CHARSET utf8");
        createTable("BlobTestblobtest", "id int not null primary key, strm blob");
        createTable("BlobTestblobtest2", "id int not null primary key, strm blob");
        createTable("conj77_test", "Name VARCHAR(100) NOT NULL,Archive LONGBLOB, PRIMARY KEY (Name)", "Engine=InnoDB DEFAULT CHARSET utf8");


    }

    @Test
    public void testPosition() throws SQLException {
        byte[] blobContent = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] pattern = new byte[]{3, 4};
        Blob blob = new MariaDbBlob(blobContent);
        assertEquals(3, blob.position(pattern, 1));
        pattern = new byte[]{12, 13};
        assertEquals(-1, blob.position(pattern, 1));
        pattern = new byte[]{11, 12};
        assertEquals(11, blob.position(pattern, 1));
        pattern = new byte[]{1, 2};
        assertEquals(1, blob.position(pattern, 1));

    }

    @Test(expected = SQLException.class)
    public void testBadStart() throws SQLException {
        byte[] blobContent = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] pattern = new byte[]{3, 4};
        Blob blob = new MariaDbBlob(blobContent);
        blob.position(pattern, 0);
    }

    @Test(expected = SQLException.class)
    public void testBadStart2() throws SQLException {
        byte[] blobContent = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] pattern = new byte[]{3, 4};
        Blob blob = new MariaDbBlob(blobContent);
        blob.position(pattern, 44);
    }

    @Test
    public void testBug716378() throws SQLException {
        Statement stmt = sharedConnection.createStatement();

        stmt.executeUpdate("insert into bug716378 values(null, 'a','b','c')");
        ResultSet rs = stmt.executeQuery("select * from bug716378");
        assertTrue(rs.next());
        byte[] arr = new byte[0];
        assertEquals(arr.getClass(), rs.getObject(2).getClass());
        assertEquals(arr.getClass(), rs.getObject(3).getClass());
        assertEquals(String.class, rs.getObject(4).getClass());
    }


    @Test
    public void testCharacterStreamWithMultibyteCharacterAndLength() throws Throwable {
        String toInsert1 = "\u00D8bbcdefgh\njklmn\"";
        String toInsert2 = "\u00D8abcdefgh\njklmn\"";
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTeststreamtest2 (id, st, strm) values (?,?,?)");
        stmt.setInt(1, 2);
        stmt.setString(2, toInsert1);
        Reader reader = new StringReader(toInsert2);
        stmt.setCharacterStream(3, reader, 5);
        stmt.execute();

        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTeststreamtest2");
        rs.next();
        Reader rdr = rs.getCharacterStream("strm");
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = rdr.read()) != -1) {
            sb.append((char) ch);
        }
        assertEquals(toInsert1, rs.getString(2));
        assertEquals(toInsert2.substring(0, 5), sb.toString());
    }

    @Test
    public void testCharacterStreamWithMultibyteCharacter() throws Throwable {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTeststreamtest3 (id, strm) values (?,?)");
        stmt.setInt(1, 2);
        String toInsert = "\u00D8abcdefgh\njklmn\"";
        Reader reader = new StringReader(toInsert);
        stmt.setCharacterStream(2, reader);
        stmt.execute();
        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTeststreamtest3");
        rs.next();
        Reader rdr = rs.getCharacterStream("strm");
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = rdr.read()) != -1) {
            sb.append((char) ch);
        }
        assertEquals(toInsert, sb.toString());
    }


    @Test
    public void testReaderWithLength() throws SQLException, IOException {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTestclobtest5 (id, strm) values (?,?)");
        byte[] arr = new byte[32000];
        Arrays.fill(arr, (byte) 'b');

        stmt.setInt(1, 1);
        String clob = new String(arr);
        stmt.setCharacterStream(2, new StringReader(clob), 20000);
        stmt.execute();
        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTestclobtest5");
        rs.next();
        Reader readStuff = rs.getCharacterStream("strm");

        char[] chars = new char[50000];
        readStuff.read(chars);

        byte[] arrResult = new byte[20000];
        Arrays.fill(arrResult, (byte) 'b');

        for (int i = 0; i < chars.length; i++) {
            if (i < 20000) {
                assertEquals(arrResult[i], chars[i]);
            } else {
                assertEquals(chars[i], '\u0000');
            }
        }
    }


    @Test
    public void testBlobWithLength() throws SQLException, IOException {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTestblobtest2 (id, strm) values (?,?)");
        byte[] arr = new byte[32000];
        Random rand = new Random();
        rand.nextBytes(arr);
        InputStream stream = new ByteArrayInputStream(arr);
        stmt.setInt(1, 1);
        stmt.setBlob(2, stream, 20000);
        stmt.execute();

        //check what stream not read after length:
        int remainRead = 0;
        while (stream.read() >= 0) {
            remainRead++;
        }
        assertEquals(12000, remainRead);

        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTestblobtest2");
        rs.next();
        InputStream readStuff = rs.getBlob("strm").getBinaryStream();
        int pos = 0;
        int ch;
        while ((ch = readStuff.read()) != -1) {
            assertEquals(arr[pos++] & 0xff, ch);
        }
        assertEquals(20000, pos);

    }

    @Test
    public void testClobWithLengthAndMultibyteCharacter() throws SQLException, IOException {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTestclobtest (id, strm) values (?,?)");
        String clob = "\u00D8clob";
        stmt.setInt(1, 1);
        stmt.setClob(2, new StringReader(clob));
        stmt.execute();
        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTestclobtest");
        if (rs.next()) {
            Reader readStuff = rs.getClob("strm").getCharacterStream();
            char[] chars = new char[5];
            readStuff.read(chars);
            assertEquals(new String(chars), clob);
        } else {
            fail();
        }

    }

    @Test
    public void testClob3() throws Exception {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTestclobtest2 (strm) values (?)");
        Clob clob = sharedConnection.createClob();
        Writer writer = clob.setCharacterStream(1);
        writer.write("\u00D8hello", 0, 6);
        writer.flush();
        stmt.setClob(1, clob);
        stmt.execute();
        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTestclobtest2");
        rs.next();
        assertTrue(rs.getObject(1) instanceof String);
        String result = rs.getString(1);
        assertEquals("\u00D8hello", result);
    }

    @Test
    public void testBlob() throws SQLException, IOException {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTestblobtest (id, strm) values (?,?)");
        byte[] theBlob = {1, 2, 3, 4, 5, 6};
        InputStream stream = new ByteArrayInputStream(theBlob);
        stmt.setInt(1, 1);
        stmt.setBlob(2, stream);
        stmt.execute();
        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTestblobtest");
        rs.next();
        InputStream readStuff = rs.getBlob("strm").getBinaryStream();
        int ch;
        int pos = 0;
        while ((ch = readStuff.read()) != -1) {
            assertEquals(theBlob[pos++], ch);
        }

        readStuff = rs.getBinaryStream("strm");

        pos = 0;
        while ((ch = readStuff.read()) != -1) {
            assertEquals(theBlob[pos++], ch);
        }
    }


    @Test
    public void testClobWithLength() throws SQLException, IOException {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTestclobtest3 (id, strm) values (?,?)");
        String clob = "clob";
        stmt.setInt(1, 1);
        stmt.setClob(2, new StringReader(clob));
        stmt.execute();
        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTestclobtest3");
        rs.next();
        Reader readStuff = rs.getClob("strm").getCharacterStream();
        char[] chars = new char[4];
        readStuff.read(chars);
        Assert.assertEquals(new String(chars), clob);
    }

    @Test
    public void testClob2() throws SQLException, IOException {
        PreparedStatement stmt = sharedConnection.prepareStatement("insert into BlobTestclobtest4 (id, strm) values (?,?)");
        Clob clob = sharedConnection.createClob();
        OutputStream ostream = clob.setAsciiStream(1);
        byte[] bytes = "hello".getBytes();
        ostream.write(bytes);
        stmt.setInt(1, 1);
        stmt.setClob(2, clob);
        stmt.execute();
        ResultSet rs = sharedConnection.createStatement().executeQuery("select * from BlobTestclobtest4");
        rs.next();
        assertTrue(rs.getObject(2) instanceof String);
        assertTrue(rs.getString(2).equals("hello"));
    }

    @Test
    public void blobSerialization() throws Exception {
        Blob blob = new MariaDbBlob(new byte[]{1, 2, 3});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(blob);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        MariaDbBlob blob2 = (MariaDbBlob) ois.readObject();
        byte[] blobBytes = blob2.getBytes(1, (int) blob2.length());
        assertEquals(3, blobBytes.length);
        assertEquals(1, blobBytes[0]);
        assertEquals(2, blobBytes[1]);
        assertEquals(3, blobBytes[2]);


        Clob clob = new MariaDbClob(new byte[]{1, 2, 3});
        baos = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(clob);

        ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        MariaDbClob c2 = (MariaDbClob) ois.readObject();
        blobBytes = c2.getBytes(1, (int) c2.length());
        assertEquals(3, blobBytes.length);
        assertEquals(1, blobBytes[0]);
        assertEquals(2, blobBytes[1]);
        assertEquals(3, blobBytes[2]);
    }

    @Test
    public void conj73() throws Exception {
       /* CONJ-73: Assertion error: UTF8 length calculation reports invalid ut8 characters */
        Clob clob = new MariaDbClob(new byte[]{(byte) 0x10, (byte) 0xD0, (byte) 0xA0, (byte) 0xe0, (byte) 0xa1, (byte) 0x8e});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(clob);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        MariaDbClob c2 = (MariaDbClob) ois.readObject();

        assertEquals(3, c2.length());
    }

    @Test
    public void conj77() throws Exception {
        try (Statement sta1 = sharedConnection.createStatement()) {
            try (PreparedStatement pre = sharedConnection.prepareStatement("INSERT INTO conj77_test (Name,Archive) VALUES (?,?)")) {
                pre.setString(1, "Empty String");
                pre.setBytes(2, "".getBytes());
                pre.addBatch();

                pre.setString(1, "Data Hello");
                pre.setBytes(2, "hello".getBytes());
                pre.addBatch();

                pre.setString(1, "Empty Data null");
                pre.setBytes(2, null);
                pre.addBatch();

                pre.executeBatch();
            }
        }

        try (Statement sta2 = sharedConnection.createStatement()) {
            try (ResultSet set = sta2.executeQuery("Select name,archive as text FROM conj77_test")) {
                while (set.next()) {
                    final Blob blob = set.getBlob("text");
                    if (blob != null) {

                        try (ByteArrayOutputStream bout = new ByteArrayOutputStream((int) blob.length())) {
                            try (InputStream bin = blob.getBinaryStream()) {
                                final byte[] buffer = new byte[1024 * 4];
                                for (int read = bin.read(buffer); read != -1; read = bin.read(buffer)) {
                                    bout.write(buffer, 0, read);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void sendEmptyBlobPreparedQuery() throws SQLException {
        createTable("emptyBlob", "test longblob, test2 text, test3 text");
        try (Connection conn = setConnection()) {
            PreparedStatement ps = conn.prepareStatement("insert into emptyBlob values(?,?,?)");
            ps.setBlob(1, new MariaDbBlob(new byte[0]));
            ps.setString(2, "a 'a ");
            ps.setNull(3, Types.VARCHAR);
            ps.executeUpdate();


            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select * from emptyBlob");
            rs.next();
            assertEquals(0, rs.getBytes(1).length);
            assertEquals("a 'a ", rs.getString(2));
            assertNull(rs.getBytes(3));
        }

    }

}
