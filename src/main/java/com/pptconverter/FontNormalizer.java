package com.pptconverter;

import java.io.IOException;
import java.util.*;
import org.w3c.dom.*;

/** Chinese editable text uses SimHei; Latin fonts change only for YaHei aliases. */
final class FontNormalizer {
    private static final String A = PptxConverter.A;
    static final String CHINESE_FONT = "SimHei";

    static int apply(Document doc) throws IOException {
        int changes = 0;
        NodeList elements = doc.getElementsByTagName("*");
        for (int i=0; i<elements.getLength(); i++) {
            Element e=(Element)elements.item(i);
            if (PptxConverter.P.equals(e.getNamespaceURI()) && "font".equals(e.getLocalName()) &&
                e.getParentNode() instanceof Element && "embeddedFont".equals(((Element)e.getParentNode()).getLocalName())) continue;
            if (e.hasAttribute("typeface")) {
                String old=e.getAttribute("typeface"), target=old;
                if (A.equals(e.getNamespaceURI()) && ("ea".equals(e.getLocalName()) ||
                    ("font".equals(e.getLocalName()) && Arrays.asList("Hans","Hant").contains(e.getAttribute("script"))))) target=CHINESE_FONT;
                else if (old.equals("微软雅黑") || old.equalsIgnoreCase("Microsoft YaHei")) target="Arial";
                if (!old.equals(target)) { e.setAttribute("typeface",target); changes++; }
            }
        }
        // Defaults cover chart labels, notes, layout/master text and inherited runs.
        for (String tag:Arrays.asList("defRPr","endParaRPr","rPr","majorFont","minorFont")) {
            for (Element e:snapshot(doc,A,tag)) changes+=font(e,"ea",CHINESE_FONT);
        }
        for (Element run:snapshot(doc,A,"r")) {
            Element text=child(run,"t");
            if (text==null || !hasChinese(text.getTextContent())) continue;
            String value=text.getTextContent();
            List<String> pieces=new ArrayList<>(); List<Boolean> chinese=new ArrayList<>();
            int start=0; boolean current=isChinese(value.codePointAt(0));
            for(int offset=0;offset<value.length();) {
                int cp=value.codePointAt(offset);boolean next=isChinese(cp);
                if(next!=current){
                    if(pieces.size()>=100000)throw new IOException("单个文本段的中英文交替次数过多，请拆分文本段后重试。");
                    pieces.add(value.substring(start,offset));chinese.add(current);start=offset;current=next;
                }
                offset+=Character.charCount(cp);
            }
            pieces.add(value.substring(start));chinese.add(current);
            if(pieces.size()==1) { changes+=setChinese(run); continue; }
            Node parent=run.getParentNode();
            for(int i=0;i<pieces.size();i++) {
                Element copy=(Element)run.cloneNode(true);
                Element t=child(copy,"t");t.setTextContent(pieces.get(i));
                t.setAttributeNS("http://www.w3.org/XML/1998/namespace","xml:space","preserve");
                if(chinese.get(i))changes+=setChinese(copy);
                parent.insertBefore(copy,run);
            }
            parent.removeChild(run);
            changes++;
        }
        // Keep field IDs and dynamic field semantics; do not split field elements.
        for(Element field:snapshot(doc,A,"fld")) {
            Element t=child(field,"t");
            if(t!=null && hasChinese(t.getTextContent())) {
                Element props=properties(field);changes+=font(props,"ea",CHINESE_FONT);
                if(t.getTextContent().codePoints().allMatch(FontNormalizer::isChinese))changes+=font(props,"latin",CHINESE_FONT);
            }
        }
        return changes;
    }
    private static int setChinese(Element run) {
        Element props=properties(run);int change="zh-CN".equals(props.getAttribute("lang"))?0:1;props.setAttribute("lang","zh-CN");
        return change+font(props,"latin",CHINESE_FONT)+font(props,"ea",CHINESE_FONT);
    }
    private static Element properties(Element run) {
        Element props=child(run,"rPr");
        if(props==null){props=run.getOwnerDocument().createElementNS(A,"a:rPr");run.insertBefore(props,run.getFirstChild());}
        return props;
    }
    private static int font(Element parent,String name,String typeface) {
        Element e=child(parent,name);
        if(e==null) {
            e=parent.getOwnerDocument().createElementNS(A,"a:"+name);
            // DrawingML fonts precede hyperlinks, rtl and extensions; theme ea precedes cs/script fonts.
            List<String> after="latin".equals(name)?Arrays.asList("ea","cs","sym","hlinkClick","hlinkMouseOver","rtl","extLst"):
                Arrays.asList("cs","sym","font","hlinkClick","hlinkMouseOver","rtl","extLst");
            Node before=null;
            for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
                if(n instanceof Element && after.contains(((Element)n).getLocalName())){before=n;break;}
            parent.insertBefore(e,before);
        } else if(typeface.equals(e.getAttribute("typeface")))return 0;
        e.setAttribute("typeface",typeface);return 1;
    }
    private static Element child(Element e,String name) {
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element && A.equals(n.getNamespaceURI()) && name.equals(n.getLocalName()))return (Element)n;
        return null;
    }
    private static List<Element> snapshot(Document d,String ns,String name) {
        NodeList nodes=d.getElementsByTagNameNS(ns,name);List<Element> result=new ArrayList<>();
        for(int i=0;i<nodes.getLength();i++)result.add((Element)nodes.item(i));return result;
    }
    private static boolean hasChinese(String s){return s.codePoints().anyMatch(FontNormalizer::isChinese);}
    private static boolean isChinese(int cp) {
        return Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN ||
            (cp>=0x3000 && cp<=0x303f) || (cp>=0xff01 && cp<=0xff60) ||
            (cp>=0x3100 && cp<=0x312f) || (cp>=0x31a0 && cp<=0x31bf);
    }
}
