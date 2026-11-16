package com.weekweeks.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/** Small, bounded reader for ordinary PDF page objects; not a general PDF renderer. */
final class PdfDocumentStreams {
    static final class Page {
        final byte[] content;
        final Map<String, UnicodeMap> fonts;
        final Set<String> imageObjects;
        final int id;
        Page(byte[] content, Map<String, UnicodeMap> fonts, Set<String> imageObjects, int id) {
            this.content = content; this.fonts = fonts; this.imageObjects = imageObjects; this.id = id;
        }
    }
    static final class Name {
        final String value;
        Name(String value) { this.value = value; }
    }
    private static final class Ref {
        final int id;
        Ref(int id) { this.id = id; }
    }
    private static final class Obj {
        final Object value;
        final byte[] stream;
        Obj(Object value, byte[] stream) { this.value = value; this.stream = stream; }
    }
    static final class UnicodeMap {
        final Map<String, String> mappings = new HashMap<>();
        int maxCodeBytes;
        void put(byte[] code, byte[] unicode) throws ImportException {
            if (code.length == 0 || code.length > 4 || unicode.length == 0 || unicode.length % 2 != 0
                    || mappings.size() >= 65536) throw invalid("unsupported ToUnicode mapping");
            String value = PdfCourseParser.strictDecode(unicode, 0, StandardCharsets.UTF_16BE);
            if (value == null) throw invalid("invalid ToUnicode Unicode value");
            mappings.put(key(code), value);
            maxCodeBytes = Math.max(maxCodeBytes, code.length);
        }
        String decode(byte[] bytes, int maxChars) throws ImportException {
            StringBuilder result = new StringBuilder();
            for (int at = 0; at < bytes.length;) {
                String mapped = null; int length = Math.min(maxCodeBytes, bytes.length - at);
                for (; length > 0; length--) {
                    byte[] code = java.util.Arrays.copyOfRange(bytes, at, at + length);
                    mapped = mappings.get(key(code));
                    if (mapped != null) break;
                }
                if (mapped == null) throw invalid("unmapped character in ToUnicode font");
                if (mapped.length() > maxChars - result.length()) throw invalid("decoded PDF text exceeds limit");
                result.append(mapped); at += length;
            }
            return result.toString();
        }
        private static String key(byte[] bytes) { return new String(bytes, StandardCharsets.ISO_8859_1); }
    }

    private static final Pattern OBJECT = Pattern.compile("(?m)(?<![A-Za-z0-9])([0-9]+)\\s+([0-9]+)\\s+obj\\b");
    private final Map<Integer, Obj> objects = new LinkedHashMap<>();
    private final Map<Integer, byte[]> decoded = new HashMap<>();
    private final Map<Integer, UnicodeMap> unicodeMaps = new HashMap<>();
    private final int limit;
    private long total;
    private PdfDocumentStreams(int limit) { this.limit = limit; }

    /** Null means the legacy stream-only fixtures have no page object model. */
    static List<Page> read(byte[] bytes, int limit) throws ImportException {
        PdfDocumentStreams document = new PdfDocumentStreams(limit);
        document.readObjects(bytes);
        List<Integer> pageIds = new ArrayList<>();
        for (Map.Entry<Integer, Obj> item : document.objects.entrySet()) {
            Map<String, Object> dictionary = dict(item.getValue().value);
            if (isName(dictionary.get("Type"), "Catalog")) {
                document.pageTree(dictionary.get("Pages"), pageIds, new HashSet<>(), 0);
                break;
            }
        }
        if (pageIds.isEmpty()) {
            for (Map.Entry<Integer, Obj> item : document.objects.entrySet()) {
                if (isName(dict(item.getValue().value).get("Type"), "Page")) pageIds.add(item.getKey());
            }
        }
        if (pageIds.isEmpty()) {
            if (!document.objects.isEmpty()) throw invalid("no supported PDF page tree");
            return null;
        }
        List<Page> result = new ArrayList<>();
        long pageContentBytes = 0;
        for (int id : pageIds) {
            Map<String, Object> page = dict(document.objects.get(id).value);
            Map<String, Object> resources = document.resources(page, new HashSet<>(), 0);
            Map<String, Object> fonts = dict(document.resolve(resources.get("Font")));
            Map<String, UnicodeMap> mappings = new HashMap<>();
            for (Map.Entry<String, Object> font : fonts.entrySet()) {
                Map<String, Object> value = dict(document.resolve(font.getValue()));
                Object cmap = value.get("ToUnicode");
                if (cmap != null) {
                    if (!(cmap instanceof Ref)) throw invalid("ToUnicode must reference a stream");
                    int mapId = ((Ref) cmap).id;
                    UnicodeMap unicode = document.unicodeMaps.get(mapId);
                    if (unicode == null) {
                        unicode = parseCMap(document.stream(cmap));
                        document.unicodeMaps.put(mapId, unicode);
                    }
                    mappings.put(font.getKey(), unicode);
                }
            }
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            document.contents(page.get("Contents"), joined, 0);
            pageContentBytes += joined.size();
            if (pageContentBytes > limit) throw invalid("expanded page content exceeds limit");
            Set<String> imageObjects = new HashSet<>();
            for (Map.Entry<String, Object> xobject : dict(document.resolve(resources.get("XObject"))).entrySet()) {
                Map<String, Object> value = dict(document.resolve(xobject.getValue()));
                if (isName(value.get("Subtype"), "Image")) imageObjects.add(xobject.getKey());
            }
            result.add(new Page(joined.toByteArray(), mappings, imageObjects, id));
        }
        return result;
    }

    private void readObjects(byte[] bytes) throws ImportException {
        String source = new String(bytes, StandardCharsets.ISO_8859_1);
        Matcher matcher = OBJECT.matcher(source);
        int position = 0;
        while (matcher.find(position)) {
            if (objects.size() >= 20000) throw invalid("too many PDF objects");
            int id;
            try { id = Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException e) { throw invalid("invalid PDF object number"); }
            Scanner scanner = new Scanner(bytes, matcher.end());
            Object value = scanner.value();
            Map<String, Object> dictionary = dict(value);
            if (isName(dictionary.get("Type"), "ObjStm"))
                throw invalid("compressed object streams are not supported");
            if (dictionary.containsKey("Encrypt")) throw invalid("encrypted PDF is not supported");
            scanner.space();
            byte[] stream = null;
            if (scanner.wordAt("stream")) {
                scanner.pos += 6;
                if (scanner.pos < bytes.length && bytes[scanner.pos] == '\r') scanner.pos++;
                if (scanner.pos < bytes.length && bytes[scanner.pos] == '\n') scanner.pos++;
                else if (scanner.pos == 0 || bytes[scanner.pos - 1] != '\r') throw invalid("missing stream line break");
                int start = scanner.pos;
                Object lengthValue = dict(value).get("Length");
                int end;
                if (lengthValue instanceof Number) {
                    long length = ((Number) lengthValue).longValue();
                    if (length < 0 || length > bytes.length - start) throw invalid("invalid stream length");
                    end = start + (int) length;
                    scanner.pos = end; scanner.space();
                    if (!scanner.wordAt("endstream")) throw invalid("stream length mismatch");
                } else {
                    // Indirect lengths are common; delimiters are only a fallback for locating raw bytes.
                    end = source.indexOf("endstream", start);
                    if (end < 0) throw invalid("unterminated stream");
                    scanner.pos = end;
                    if (!(lengthValue instanceof Ref)) throw invalid("missing stream length");
                    // Keep delimiter bytes until the referenced Length object is available.
                }
                stream = java.util.Arrays.copyOfRange(bytes, start, end);
                scanner.pos += 9;
            }
            scanner.space();
            if (!scanner.wordAt("endobj")) throw invalid("unterminated PDF object");
            objects.put(id, new Obj(value, stream));
            position = scanner.pos + 6;
        }
        // Encryption is a trailer property, not a content-stream token.
        int trailer = source.indexOf("trailer", position);
        if (trailer >= 0) {
            Object value = new Scanner(bytes, trailer + 7).value();
            if (dict(value).containsKey("Encrypt")) throw invalid("encrypted PDF is not supported");
        }
    }

    private void pageTree(Object node, List<Integer> pages, Set<Integer> visited, int depth) throws ImportException {
        if (!(node instanceof Ref) || depth > 32 || !visited.add(((Ref) node).id)) throw invalid("invalid page tree");
        int id = ((Ref) node).id;
        Map<String, Object> value = dict(resolve(node));
        if (isName(value.get("Type"), "Page")) { pages.add(id); return; }
        Object kids = resolve(value.get("Kids"));
        if (!(kids instanceof List)) throw invalid("missing page tree children");
        for (Object kid : (List<?>) kids) pageTree(kid, pages, visited, depth + 1);
    }
    private Map<String, Object> resources(Map<String, Object> page, Set<Integer> visited, int depth) throws ImportException {
        if (depth > 32) throw invalid("invalid inherited resources");
        if (page.containsKey("Resources")) return dict(resolve(page.get("Resources")));
        Object parent = page.get("Parent");
        if (!(parent instanceof Ref)) return Collections.emptyMap();
        if (!visited.add(((Ref) parent).id)) throw invalid("cyclic inherited resources");
        return resources(dict(resolve(parent)), visited, depth + 1);
    }
    private Object resolve(Object value) throws ImportException {
        if (!(value instanceof Ref)) return value;
        Obj object = objects.get(((Ref) value).id);
        if (object == null) throw invalid("missing referenced PDF object");
        return object.value;
    }
    private void contents(Object value, ByteArrayOutputStream out, int depth) throws ImportException {
        if (depth > 16) throw invalid("nested content arrays");
        if (value == null) return;
        Object resolved = resolve(value);
        if (resolved instanceof List) {
            for (Object part : (List<?>) resolved) contents(part, out, depth + 1);
        } else {
            byte[] part = stream(value);
            if ((long) out.size() + part.length + 1 > limit) throw invalid("PDF content exceeds limit");
            out.write(part, 0, part.length); out.write('\n');
        }
    }
    private byte[] stream(Object reference) throws ImportException {
        if (!(reference instanceof Ref)) throw invalid("stream must be indirect");
        int id = ((Ref) reference).id;
        if (decoded.containsKey(id)) return decoded.get(id);
        Obj object = objects.get(id);
        if (object == null || object.stream == null) throw invalid("missing PDF stream");
        Map<String, Object> dictionary = dict(object.value);
        Object filters = dictionary.get("Filter");
        if (filters instanceof List && ((List<?>) filters).size() == 1) filters = ((List<?>) filters).get(0);
        if (dictionary.get("DecodeParms") != null && !dict(dictionary.get("DecodeParms")).isEmpty())
            throw invalid("stream decode parameters are not supported");
        byte[] bytes = object.stream;
        Object storedLength = dictionary.get("Length");
        if (storedLength instanceof Ref) {
            Object resolvedLength = resolve(storedLength);
            if (!(resolvedLength instanceof Number)) throw invalid("invalid indirect stream length");
            long length = ((Number) resolvedLength).longValue();
            if (length < 0 || length > bytes.length) throw invalid("indirect stream length mismatch");
            for (int i = (int) length; i < bytes.length; i++) {
                if (bytes[i] != '\r' && bytes[i] != '\n') throw invalid("indirect stream length mismatch");
            }
            bytes = java.util.Arrays.copyOf(bytes, (int) length);
        }
        if (filters != null) {
            if (!isName(filters, "FlateDecode") && !isName(filters, "Fl")) throw invalid("unsupported PDF stream filter");
            try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int read;
                while ((read = input.read(buffer)) >= 0) {
                    if ((long) out.size() + read > limit) throw invalid("inflated PDF content exceeds limit");
                    out.write(buffer, 0, read);
                }
                bytes = out.toByteArray();
            } catch (ImportException e) { throw e; }
            catch (Exception e) { throw invalid("invalid compressed PDF stream"); }
        }
        total += bytes.length;
        if (total > limit) throw invalid("PDF streams exceed limit");
        decoded.put(id, bytes); return bytes;
    }

    private static UnicodeMap parseCMap(byte[] bytes) throws ImportException {
        Scanner scanner = new Scanner(bytes, 0); List<Object> operands = new ArrayList<>();
        UnicodeMap map = new UnicodeMap();
        while (scanner.hasNext()) {
            Object token = scanner.next();
            if ("beginbfchar".equals(token) || "beginbfrange".equals(token)) {
                if (operands.isEmpty() || !(operands.get(operands.size() - 1) instanceof Number)) throw invalid("invalid CMap count");
                double declaredCount = ((Number) operands.get(operands.size() - 1)).doubleValue();
                if (declaredCount < 0 || declaredCount > 65536 || declaredCount != Math.rint(declaredCount))
                    throw invalid("CMap count must be a bounded integer");
                int count = (int) declaredCount;
                boolean range = "beginbfrange".equals(token);
                for (int i = 0; i < count; i++) {
                    byte[] start = string(scanner.next());
                    if (!range) { map.put(start, string(scanner.next())); continue; }
                    byte[] end = string(scanner.next()); Object destination = scanner.next();
                    if (start.length != end.length || start.length == 0 || start.length > 4) throw invalid("invalid CMap range");
                    long first = unsigned(start), last = unsigned(end);
                    if (last < first || last - first > 65535) throw invalid("CMap range exceeds limit");
                    if (destination instanceof List && ((List<?>) destination).size() != last - first + 1) throw invalid("CMap array length mismatch");
                    byte[] current = destination instanceof byte[] ? ((byte[]) destination).clone() : null;
                    for (long code = first; code <= last; code++) {
                        byte[] source = integerBytes(code, start.length);
                        if (current != null) {
                            map.put(source, current); increment(current);
                        } else if (destination instanceof List) {
                            map.put(source, string(((List<?>) destination).get((int) (code - first))));
                        } else throw invalid("invalid CMap destination");
                    }
                }
                Object end = scanner.next();
                if (!(range ? "endbfrange" : "endbfchar").equals(end)) throw invalid("unterminated CMap block");
                operands.clear();
            } else if ("usecmap".equals(token)) throw invalid("inherited CMap is not supported");
            else {
                operands.add(token);
                if (operands.size() > 20) operands.remove(0);
            }
        }
        if (map.mappings.isEmpty()) throw invalid("empty ToUnicode map");
        return map;
    }
    private static long unsigned(byte[] bytes) { long n = 0; for (byte b : bytes) n = (n << 8) | (b & 255); return n; }
    private static byte[] integerBytes(long n, int length) { byte[] result = new byte[length]; for (int i=length-1;i>=0;i--) { result[i]=(byte)n; n>>>=8; } return result; }
    private static void increment(byte[] value) { for(int i=value.length-1;i>=0;i--) if (++value[i] != 0) break; }
    private static byte[] string(Object value) throws ImportException { if (!(value instanceof byte[])) throw invalid("expected PDF string"); return (byte[]) value; }
    @SuppressWarnings("unchecked") private static Map<String, Object> dict(Object value) { return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap(); }
    private static boolean isName(Object value, String name) { return value instanceof Name && name.equals(((Name) value).value); }
    static ImportException invalid(String detail) { return new ImportException(UiLang.t("PDF 结构暂不支持或已损坏: ", "PDF 結構暫不支援或已損壞: ", "Unsupported or damaged PDF: ") + detail); }

    /** Lexer shared by object dictionaries, CMaps and text operators; strings are never operators. */
    static final class Scanner {
        final byte[] bytes; int pos; int tokens; int depth;
        Scanner(byte[] bytes, int pos) { this.bytes = bytes; this.pos = pos; }
        boolean hasNext() { space(); return pos < bytes.length; }
        boolean wordAt(String value) {
            if (pos + value.length() > bytes.length) return false;
            for(int i=0;i<value.length();i++) if(bytes[pos+i] != value.charAt(i)) return false;
            return pos + value.length() == bytes.length || delimiter(bytes[pos+value.length()] & 255);
        }
        void space() {
            while(pos < bytes.length) {
                int b=bytes[pos]&255;
                if (white(b)) { pos++; continue; }
                if (b=='%') { while(pos<bytes.length && bytes[pos]!='\r' && bytes[pos]!='\n') pos++; continue; }
                break;
            }
        }
        Object value() throws ImportException {
            Object first = next();
            if (!(first instanceof Number)) return first;
            int saved = pos;
            Object second = next(); Object third = second instanceof Number ? next() : null;
            if (second instanceof Number && "R".equals(third)) return new Ref(((Number) first).intValue());
            pos = saved; return first;
        }
        Object next() throws ImportException {
            space(); if (pos >= bytes.length) return null;
            if (++tokens > 1000000 || depth > 40) throw invalid("PDF token limit");
            int b = bytes[pos++] & 255;
            if (b == '(') {
                ByteArrayOutputStream out = new ByteArrayOutputStream(); int nesting=1;
                while(pos<bytes.length && nesting>0) {
                    int c=bytes[pos++]&255;
                    if(c=='\\') {
                        if(pos>=bytes.length) throw invalid("unterminated string escape");
                        c=bytes[pos++]&255;
                        if(c=='\r' || c=='\n') { if(c=='\r' && pos<bytes.length && bytes[pos]=='\n')pos++; continue; }
                        if(c>='0'&&c<='7') { int n=c-'0'; for(int i=0;i<2&&pos<bytes.length&&bytes[pos]>='0'&&bytes[pos]<='7';i++)n=n*8+bytes[pos++]-'0'; out.write(n&255); continue; }
                        switch(c){case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;case 'b':c='\b';break;case 'f':c='\f';break;default:break;}
                        out.write(c); continue;
                    }
                    if(c=='(')nesting++; else if(c==')'&&--nesting==0)break;
                    out.write(c);
                }
                if(nesting!=0)throw invalid("unterminated literal string");
                return out.toByteArray();
            }
            if(b=='<' && pos<bytes.length && bytes[pos]=='<') {
                pos++; depth++; Map<String,Object> map=new LinkedHashMap<>();
                while(hasNext()) {
                    if(bytes[pos]=='>'&&pos+1<bytes.length&&bytes[pos+1]=='>'){pos+=2;depth--;return map;}
                    Object key=next();if(!(key instanceof Name))throw invalid("invalid dictionary key");
                    map.put(((Name)key).value,value());
                }
                throw invalid("unterminated dictionary");
            }
            if(b=='<') {
                ByteArrayOutputStream out=new ByteArrayOutputStream(); int high=-1;
                while(pos<bytes.length) {
                    int c=bytes[pos++]&255;
                    if(c=='>'){if(high>=0)out.write(high<<4);return out.toByteArray();}
                    if(white(c))continue;
                    int digit=Character.digit((char)c,16);if(digit<0)throw invalid("invalid hex string");
                    if(high<0)high=digit;else{out.write(high*16+digit);high=-1;}
                }
                throw invalid("unterminated hex string");
            }
            if(b=='[') {
                depth++;List<Object> list=new ArrayList<>();
                while(hasNext()){if(bytes[pos]==']'){pos++;depth--;return list;}list.add(value());}
                throw invalid("unterminated array");
            }
            if(b=='/') {
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                while(pos<bytes.length&&!delimiter(bytes[pos]&255)) {
                    int c=bytes[pos++]&255;
                    if(c=='#'&&pos+1<bytes.length){int h=Character.digit((char)bytes[pos],16),l=Character.digit((char)bytes[pos+1],16);if(h>=0&&l>=0){c=h*16+l;pos+=2;}}
                    out.write(c);
                }
                return new Name(new String(out.toByteArray(),StandardCharsets.ISO_8859_1));
            }
            int start=pos-1;while(pos<bytes.length&&!delimiter(bytes[pos]&255))pos++;
            String word=new String(bytes,start,pos-start,StandardCharsets.ISO_8859_1);
            try { double number=Double.parseDouble(word);if(!Double.isFinite(number))throw invalid("nonfinite PDF number");return number; }
            catch(NumberFormatException ignored){return word;}
        }
        private static boolean white(int c){return c==0||c==9||c==10||c==12||c==13||c==32;}
        private static boolean delimiter(int c){return white(c)||c=='('||c==')'||c=='<'||c=='>'||c=='['||c==']'||c=='{'||c=='}'||c=='/'||c=='%';}
    }
}
