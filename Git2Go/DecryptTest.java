public class DecryptTest {
    public static void main(String[] args) {
        String env = "NQeHgeomUPig5fEoKpkQ6sG9Wxp+z/UxlCvFtAG7s9NCtdxI8kGJ3N5r1stWiHz7Rxz1MWpTTNv8jpul985k+8yPce1CMsBVuJaThA8e/GrC/mEHHwxs9wYBpBXXOiquCl+y5xoD5mn5";
        System.out.println("DECRYPTED=[" + EnvEncryptor.decrypt(env) + "]");
    }
}
