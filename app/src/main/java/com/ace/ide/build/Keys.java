package com.ace.ide.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Signing key helpers: generate (self-signed RSA), load PKCS12 / BKS / JKS keystores, fingerprints. */
public final class Keys {

    public static final class KeyInfo {
        public PrivateKey key;
        public X509Certificate[] chain;
        public String alias;
    }

    private Keys() {}

    // ------------------------------------------------------------------ generate

    public static void generate(File out, String alias, char[] pass, String cn, String ou, String o,
                                String l, String st, String c, int years, int bits) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(bits, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        byte[] name = name(c, st, l, o, ou, cn);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date from = cal.getTime();
        cal.add(Calendar.YEAR, Math.max(1, years));
        Date to = cal.getTime();

        byte[] sigAlg = seq(oid("1.2.840.113549.1.1.11"), new byte[]{0x05, 0x00});
        byte[] serial = new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE).toByteArray();
        byte[] tbs = seq(
                tlv(0xA0, tlv(0x02, new byte[]{2})),
                tlv(0x02, serial),
                sigAlg,
                name,
                seq(time(from), time(to)),
                name,
                kp.getPublic().getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bitString = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bitString, 1, sig.length);
        byte[] cert = seq(tbs, sigAlg, tlv(0x03, bitString));

        X509Certificate x509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(cert));
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, kp.getPrivate(), pass, new Certificate[]{x509});
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream fo = new FileOutputStream(out);
        try { ks.store(fo, pass); } finally { fo.close(); }
    }

    private static byte[] name(String c, String st, String l, String o, String ou, String cn) throws IOException {
        List<byte[]> rdns = new ArrayList<byte[]>();
        add(rdns, "2.5.4.6", c, 0x13);
        add(rdns, "2.5.4.8", st, 0x0C);
        add(rdns, "2.5.4.7", l, 0x0C);
        add(rdns, "2.5.4.10", o, 0x0C);
        add(rdns, "2.5.4.11", ou, 0x0C);
        add(rdns, "2.5.4.3", cn, 0x0C);
        if (rdns.isEmpty()) add(rdns, "2.5.4.3", "Unknown", 0x0C);
        return seq(rdns.toArray(new byte[0][]));
    }

    private static void add(List<byte[]> l, String oid, String v, int tag) throws IOException {
        if (v == null || v.trim().length() == 0) return;
        l.add(tlv(0x31, seq(oid(oid), tlv(tag, v.trim().getBytes("UTF-8")))));
    }

    private static byte[] time(Date d) throws IOException {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTime(d);
        boolean utc = c.get(Calendar.YEAR) < 2050;
        SimpleDateFormat f = new SimpleDateFormat(utc ? "yyMMddHHmmss'Z'" : "yyyyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(utc ? 0x17 : 0x18, f.format(d).getBytes("US-ASCII"));
    }

    private static byte[] oid(String s) throws IOException {
        String[] p = s.split("\\.");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            byte[] tmp = new byte[10];
            int n = 0;
            tmp[n++] = (byte) (v & 0x7F);
            v >>= 7;
            while (v > 0) { tmp[n++] = (byte) ((v & 0x7F) | 0x80); v >>= 7; }
            for (int k = n - 1; k >= 0; k--) b.write(tmp[k]);
        }
        return tlv(0x06, b.toByteArray());
    }

    private static byte[] seq(byte[]... parts) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) b.write(p);
        return tlv(0x30, b.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] v) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(tag);
        int n = v.length;
        if (n < 0x80) b.write(n);
        else if (n < 0x100) { b.write(0x81); b.write(n); }
        else if (n < 0x10000) { b.write(0x82); b.write(n >> 8); b.write(n); }
        else { b.write(0x83); b.write(n >> 16); b.write(n >> 8); b.write(n); }
        b.write(v);
        return b.toByteArray();
    }

    // ------------------------------------------------------------------ load

    /** @param alias null = first key entry */
    public static KeyInfo load(File f, char[] storePass, String alias, char[] keyPass) throws Exception {
        byte[] data = ApkZip.readAll(f);
        if (data.length > 4 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xED
                && (data[2] & 0xFF) == 0xFE && (data[3] & 0xFF) == 0xED) {
            return loadJks(data, storePass, alias);
        }
        Exception last = null;
        for (String type : new String[]{"PKCS12", "BKS", "JKS"}) {
            try {
                KeyStore ks = KeyStore.getInstance(type);
                ks.load(new ByteArrayInputStream(data), storePass);
                String a = alias;
                if (a == null || a.length() == 0) {
                    Enumeration<String> en = ks.aliases();
                    while (en.hasMoreElements()) {
                        String x = en.nextElement();
                        if (ks.isKeyEntry(x)) { a = x; break; }
                    }
                }
                if (a == null) throw new IOException("keystore has no private key");
                PrivateKey k = (PrivateKey) ks.getKey(a, keyPass != null ? keyPass : storePass);
                if (k == null) throw new IOException("alias '" + a + "' has no private key (wrong alias / key password?)");
                Certificate[] cs = ks.getCertificateChain(a);
                KeyInfo ki = new KeyInfo();
                ki.key = k;
                ki.alias = a;
                ki.chain = new X509Certificate[cs.length];
                for (int i = 0; i < cs.length; i++) ki.chain[i] = (X509Certificate) cs[i];
                return ki;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("unsupported keystore");
    }

    public static List<String> aliases(File f, char[] storePass) {
        List<String> out = new ArrayList<String>();
        try {
            byte[] data = ApkZip.readAll(f);
            if (data.length > 4 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xED) {
                parseJks(data, storePass, null, out);
                return out;
            }
            for (String type : new String[]{"PKCS12", "BKS"}) {
                try {
                    KeyStore ks = KeyStore.getInstance(type);
                    ks.load(new ByteArrayInputStream(data), storePass);
                    Enumeration<String> en = ks.aliases();
                    while (en.hasMoreElements()) {
                        String a = en.nextElement();
                        if (ks.isKeyEntry(a)) out.add(a);
                    }
                    return out;
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ---- JKS (Sun/Oracle format, which Android cannot open natively)

    private static KeyInfo loadJks(byte[] data, char[] pass, String alias) throws Exception {
        List<String> names = new ArrayList<String>();
        KeyInfo ki = parseJks(data, pass, alias, names);
        if (ki == null) throw new IOException("no private key entry" + (alias != null ? " named '" + alias + "'" : "") + " in JKS");
        return ki;
    }

    private static KeyInfo parseJks(byte[] data, char[] pass, String wantAlias, List<String> aliasesOut) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        in.readInt();
        int version = in.readInt();
        int count = in.readInt();
        KeyInfo found = null;
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < count; i++) {
            int tag = in.readInt();
            String alias = in.readUTF();
            in.readLong();
            if (tag == 1) {
                byte[] enc = new byte[in.readInt()];
                in.readFully(enc);
                int n = in.readInt();
                List<X509Certificate> chain = new ArrayList<X509Certificate>();
                for (int k = 0; k < n; k++) {
                    if (version == 2) in.readUTF();
                    byte[] cb = new byte[in.readInt()];
                    in.readFully(cb);
                    chain.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cb)));
                }
                aliasesOut.add(alias);
                if (found == null && (wantAlias == null || wantAlias.length() == 0 || wantAlias.equals(alias))) {
                    byte[] pkcs8 = jksDecrypt(enc, pass);
                    found = new KeyInfo();
                    found.alias = alias;
                    found.key = privateKey(pkcs8);
                    found.chain = chain.toArray(new X509Certificate[0]);
                }
            } else if (tag == 2) {
                if (version == 2) in.readUTF();
                byte[] cb = new byte[in.readInt()];
                in.readFully(cb);
            } else {
                throw new IOException("corrupt JKS");
            }
        }
        return found;
    }

    private static PrivateKey privateKey(byte[] pkcs8) throws Exception {
        Exception last = null;
        for (String alg : new String[]{"RSA", "EC", "DSA"}) {
            try { return KeyFactory.getInstance(alg).generatePrivate(new PKCS8EncodedKeySpec(pkcs8)); }
            catch (Exception e) { last = e; }
        }
        throw last;
    }

    /** EncryptedPrivateKeyInfo with Sun's proprietary SHA-1 stream cipher */
    private static byte[] jksDecrypt(byte[] epki, char[] pass) throws Exception {
        int[] pos = {0};
        readTag(epki, pos);                 // SEQUENCE
        readTag(epki, pos);                 // AlgorithmIdentifier SEQUENCE
        int algLen = readLen(epki, pos);
        pos[0] += algLen;
        if ((epki[pos[0]++] & 0xFF) != 0x04) throw new IOException("bad key blob");
        int len = readLen(epki, pos);
        byte[] blob = new byte[len];
        System.arraycopy(epki, pos[0], blob, 0, len);

        byte[] pw = new byte[pass.length * 2];
        for (int i = 0; i < pass.length; i++) { pw[2 * i] = (byte) (pass[i] >> 8); pw[2 * i + 1] = (byte) pass[i]; }
        int plainLen = blob.length - 40;
        if (plainLen <= 0) throw new IOException("bad key blob");
        byte[] salt = new byte[20];
        System.arraycopy(blob, 0, salt, 0, 20);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] plain = new byte[plainLen];
        byte[] digest = salt;
        int off = 0;
        while (off < plainLen) {
            md.update(pw);
            md.update(digest);
            digest = md.digest();
            for (int k = 0; k < digest.length && off < plainLen; k++, off++) {
                plain[off] = (byte) (blob[20 + off] ^ digest[k]);
            }
        }
        md.update(pw);
        md.update(plain);
        byte[] check = md.digest();
        for (int i = 0; i < 20; i++) {
            if (check[i] != blob[blob.length - 20 + i]) throw new IOException("wrong password for JKS key");
        }
        return plain;
    }

    private static void readTag(byte[] b, int[] pos) throws IOException {
        pos[0]++;
        readLen(b, pos);
    }

    private static int readLen(byte[] b, int[] pos) {
        int l = b[pos[0]++] & 0xFF;
        if (l < 0x80) return l;
        int n = l & 0x7F;
        int v = 0;
        for (int i = 0; i < n; i++) v = (v << 8) | (b[pos[0]++] & 0xFF);
        return v;
    }

    // ------------------------------------------------------------------ info

    public static String fingerprint(X509Certificate c, String alg) {
        try {
            byte[] d = MessageDigest.getInstance(alg).digest(c.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format(Locale.US, "%02X", d[i] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return "?";
        }
    }

    public static String describe(KeyInfo k) {
        X509Certificate c = k.chain[0];
        return "Alias: " + k.alias + "\nOwner: " + c.getSubjectDN() + "\nValid: " + c.getNotBefore() + "  →  " + c.getNotAfter()
                + "\nSHA-1: " + fingerprint(c, "SHA-1") + "\nSHA-256: " + fingerprint(c, "SHA-256");
    }

    /** the default debug key, created on first use */
    public static KeyInfo debugKey(File store) throws Exception {
        char[] pw = "android".toCharArray();
        if (!store.exists() || store.length() == 0) {
            generate(store, "acedebug", pw, "ACE Debug", "ACE", "Android Code Environment", "", "", "US", 30, 2048);
        }
        try {
            return load(store, pw, "acedebug", pw);
        } catch (Exception e) {
            store.delete();
            generate(store, "acedebug", pw, "ACE Debug", "ACE", "Android Code Environment", "", "", "US", 30, 2048);
            return load(store, pw, "acedebug", pw);
        }
    }
}
