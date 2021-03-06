/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.utils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.hornetq.core.client.HornetQClientLogger;
import org.hornetq.core.client.HornetQClientMessageBundle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
public final class XMLUtil
{

   private XMLUtil()
   {
      // Utility class
   }

   public static Element stringToElement(final String s) throws Exception
   {
      return XMLUtil.readerToElement(new StringReader(s));
   }

   public static Element urlToElement(final URL url) throws Exception
   {
      return XMLUtil.readerToElement(new InputStreamReader(url.openStream()));
   }

   public static String readerToString(final Reader r) throws Exception
   {
      // Read into string
      StringBuilder buff = new StringBuilder();
      int c;
      while ((c = r.read()) != -1)
      {
         buff.append((char)c);
      }
      return buff.toString();
   }

   public static Element readerToElement(final Reader r) throws Exception
   {
      // Read into string
      StringBuffer buff = new StringBuffer();
      int c;
      while ((c = r.read()) != -1)
      {
         buff.append((char)c);
      }

      // Quick hardcoded replace, FIXME this is a kludge - use regexp to match properly
      String s = buff.toString();

      StringReader sreader = new StringReader(s);

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6529766
      factory.setNamespaceAware(true);
      DocumentBuilder parser = factory.newDocumentBuilder();
      Document doc = parser.parse(new InputSource(sreader));
      return doc.getDocumentElement();
   }

   public static String elementToString(final Node n)
   {

      String name = n.getNodeName();

      short type = n.getNodeType();

      if (Node.CDATA_SECTION_NODE == type)
      {
         return "<![CDATA[" + n.getNodeValue() + "]]>";
      }

      if (name.startsWith("#"))
      {
         return "";
      }

      StringBuffer sb = new StringBuffer();
      sb.append('<').append(name);

      NamedNodeMap attrs = n.getAttributes();
      if (attrs != null)
      {
         for (int i = 0; i < attrs.getLength(); i++)
         {
            Node attr = attrs.item(i);
            sb.append(' ').append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\"");
         }
      }

      String textContent = null;
      NodeList children = n.getChildNodes();

      if (children.getLength() == 0)
      {
         if ((textContent = XMLUtil.getTextContent(n)) != null && !"".equals(textContent))
         {
            sb.append(textContent).append("</").append(name).append('>');;
         }
         else
         {
            sb.append("/>").append('\n');
         }
      }
      else
      {
         sb.append('>').append('\n');
         boolean hasValidChildren = false;
         for (int i = 0; i < children.getLength(); i++)
         {
            String childToString = XMLUtil.elementToString(children.item(i));
            if (!"".equals(childToString))
            {
               sb.append(childToString);
               hasValidChildren = true;
            }
         }

         if (!hasValidChildren && (textContent = XMLUtil.getTextContent(n)) != null)
         {
            sb.append(textContent);
         }

         sb.append("</").append(name).append('>');
      }

      return sb.toString();
   }

   private static final Object[] EMPTY_ARRAY = new Object[0];

   /**
    * This metod is here because Node.getTextContent() is not available in JDK 1.4 and I would like
    * to have an uniform access to this functionality.
    *
    * Note: if the content is another element or set of elements, it returns a string representation
    *       of the hierarchy.
    *
    * TODO implementation of this method is a hack. Implement it properly.
    */
   public static String getTextContent(final Node n)
   {
      if (n.hasChildNodes())
      {
         StringBuffer sb = new StringBuffer();
         NodeList nl = n.getChildNodes();
         for (int i = 0; i < nl.getLength(); i++)
         {
            sb.append(XMLUtil.elementToString(nl.item(i)));
            if (i < nl.getLength() - 1)
            {
               sb.append('\n');
            }
         }

         String s = sb.toString();
         if (s.length() != 0)
         {
            return s;
         }
      }

      Method[] methods = Node.class.getMethods();

      for (Method getTextContext : methods)
      {
         if ("getTextContent".equals(getTextContext.getName()))
         {
            try
            {
               return (String)getTextContext.invoke(n, XMLUtil.EMPTY_ARRAY);
            }
            catch (Exception e)
            {
               HornetQClientLogger.LOGGER.errorOnXMLTransform(e, n);
               return null;
            }
         }
      }

      String textContent = null;

      if (n.hasChildNodes())
      {
         NodeList nl = n.getChildNodes();
         for (int i = 0; i < nl.getLength(); i++)
         {
            Node c = nl.item(i);
            if (c.getNodeType() == Node.TEXT_NODE)
            {
               textContent = n.getNodeValue();
               if (textContent == null)
               {
                  // TODO This is a hack. Get rid of it and implement this properly
                  String s = c.toString();
                  int idx = s.indexOf("#text:");
                  if (idx != -1)
                  {
                     textContent = s.substring(idx + 6).trim();
                     if (textContent.endsWith("]"))
                     {
                        textContent = textContent.substring(0, textContent.length() - 1);
                     }
                  }
               }
               if (textContent == null)
               {
                  break;
               }
            }
         }

         // TODO This is a hack. Get rid of it and implement this properly
         String s = n.toString();
         int i = s.indexOf('>');
         int i2 = s.indexOf("</");
         if (i != -1 && i2 != -1)
         {
            textContent = s.substring(i + 1, i2);
         }
      }

      return textContent;
   }

   public static void assertEquivalent(final Node node, final Node node2)
   {
      if (node == null)
      {
         throw HornetQClientMessageBundle.BUNDLE.firstNodeNull();
      }

      if (node2 == null)
      {
         throw HornetQClientMessageBundle.BUNDLE.secondNodeNull();
      }

      if (!node.getNodeName().equals(node2.getNodeName()))
      {
         throw HornetQClientMessageBundle.BUNDLE.nodeHaveDifferentNames();
      }

      int attrCount = 0;
      NamedNodeMap attrs = node.getAttributes();
      if (attrs != null)
      {
         attrCount = attrs.getLength();
      }

      int attrCount2 = 0;
      NamedNodeMap attrs2 = node2.getAttributes();
      if (attrs2 != null)
      {
         attrCount2 = attrs2.getLength();
      }

      if (attrCount != attrCount2)
      {
         throw HornetQClientMessageBundle.BUNDLE.nodeHaveDifferentAttNumber();
      }

      outer: for (int i = 0; i < attrCount; i++)
      {
         Node n = attrs.item(i);
         String name = n.getNodeName();
         String value = n.getNodeValue();

         for (int j = 0; j < attrCount; j++)
         {
            Node n2 = attrs2.item(j);
            String name2 = n2.getNodeName();
            String value2 = n2.getNodeValue();

            if (name.equals(name2) && value.equals(value2))
            {
               continue outer;
            }
         }
         throw HornetQClientMessageBundle.BUNDLE.attsDontMatch(name, value);
      }

      boolean hasChildren = node.hasChildNodes();

      if (hasChildren != node2.hasChildNodes())
      {
         throw HornetQClientMessageBundle.BUNDLE.oneNodeHasChildren();
      }

      if (hasChildren)
      {
         NodeList nl = node.getChildNodes();
         NodeList nl2 = node2.getChildNodes();

         short[] toFilter = new short[] { Node.TEXT_NODE, Node.ATTRIBUTE_NODE, Node.COMMENT_NODE };
         List<Node> nodes = XMLUtil.filter(nl, toFilter);
         List<Node> nodes2 = XMLUtil.filter(nl2, toFilter);

         int length = nodes.size();

         if (length != nodes2.size())
         {
            throw HornetQClientMessageBundle.BUNDLE.nodeHasDifferentChildNumber();
         }

         for (int i = 0; i < length; i++)
         {
            Node n = nodes.get(i);
            Node n2 = nodes2.get(i);
            XMLUtil.assertEquivalent(n, n2);
         }
      }
   }

   public static String stripCDATA(String s)
   {
      s = s.trim();
      if (s.startsWith("<![CDATA["))
      {
         s = s.substring(9);
         int i = s.indexOf("]]>");
         if (i == -1)
         {
            throw new IllegalStateException("argument starts with <![CDATA[ but cannot find pairing ]]>");
         }
         s = s.substring(0, i);
      }
      return s;
   }

   /* public static String replaceSystemProps(String xml)
    {
       Properties properties = System.getProperties();
       Enumeration e = properties.propertyNames();
       while (e.hasMoreElements())
       {
          String key = (String)e.nextElement();
          String s = "${" + key + "}";
          if (xml.contains(s))
          {
             xml = xml.replace(s, properties.getProperty(key));
          }

       }
       return xml;
    }*/
   public static String replaceSystemProps(String xml)
   {
      while (xml.contains("${"))
      {
         int start = xml.indexOf("${");
         int end = xml.indexOf("}") + 1;
         if (end < 0)
         {
            break;
         }
         String subString = xml.substring(start, end);
         String prop = subString.substring(2, subString.length() - 1).trim();
         String val = "";
         if (prop.contains(":"))
         {
            String[] parts = prop.split(":", 2);
            prop = parts[0].trim();
            val = parts[1].trim();
         }
         String sysProp = System.getProperty(prop, val);
         HornetQClientLogger.LOGGER.debug("replacing " + subString + " with " + sysProp);
         xml = xml.replace(subString, sysProp);

      }
      return xml;
   }

   public static long parseLong(final Node elem)
   {
      String value = elem.getTextContent().trim();

      try
      {
         return Long.parseLong(value);
      }
      catch (NumberFormatException e)
      {
         throw HornetQClientMessageBundle.BUNDLE.mustBeLong(elem, value);
      }
   }

   public static int parseInt(final Node elem)
   {
      String value = elem.getTextContent().trim();

      try
      {
         return Integer.parseInt(value);
      }
      catch (NumberFormatException e)
      {
         throw HornetQClientMessageBundle.BUNDLE.mustBeInteger(elem, value);
      }
   }

   public static boolean parseBoolean(final Node elem)
   {
      String value = elem.getTextContent().trim();

      try
      {
         return Boolean.parseBoolean(value);
      }
      catch (NumberFormatException e)
      {
         throw HornetQClientMessageBundle.BUNDLE.mustBeBoolean(elem, value);
      }
   }

   public static double parseDouble(final Node elem)
   {
      String value = elem.getTextContent().trim();

      try
      {
         return Double.parseDouble(value);
      }
      catch (NumberFormatException e)
      {
         throw HornetQClientMessageBundle.BUNDLE.mustBeDouble(elem, value);
      }
   }

   public static void validate(final Node node, final String schemaFile) throws Exception
   {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

      Schema schema = factory.newSchema(findResource(schemaFile));
      Validator validator = schema.newValidator();

      // validate the DOM tree
      try
      {
         validator.validate(new DOMSource(node));
      }
      catch (SAXException e)
      {
         HornetQClientLogger.LOGGER.errorOnXMLTransformInvalidConf(e);

         throw new IllegalStateException("Invalid configuration", e);
      }
   }

   private static List<Node> filter(final NodeList nl, final short[] typesToFilter)
   {
      List<Node> nodes = new ArrayList<Node>();

      outer: for (int i = 0; i < nl.getLength(); i++)
      {
         Node n = nl.item(i);
         short type = n.getNodeType();
         for (int j = 0; j < typesToFilter.length; j++)
         {
            if (typesToFilter[j] == type)
            {
               continue outer;
            }
         }
         nodes.add(n);
      }
      return nodes;
   }

   private static URL findResource(final String resourceName)
   {
      return AccessController.doPrivileged(new PrivilegedAction<URL>()
      {
         public URL run()
         {
            return ClassloadingUtil.findResource(resourceName);
         }
      });
   }


   // Inner classes --------------------------------------------------------------------------------

}
