/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.resources.rss;

import java.io.ByteArrayOutputStream;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;

public final class RSSWriter {

    private RSSWriter() {

    }

    public static void write(RSSFeed rssfeed, ByteArrayOutputStream byteArrayOutputStream) throws Exception {
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLEventWriter writer = output.createXMLEventWriter(byteArrayOutputStream);
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();

        StartDocument startDocument = eventFactory.createStartDocument();
        writer.add(startDocument);
        StartElement rssStart = eventFactory.createStartElement("", "", "rss");
        writer.add(rssStart);
        writer.add(eventFactory.createAttribute("version", "2.0"));

        writer.add(eventFactory.createStartElement("", "", "channel"));

        RSSHeader header = rssfeed.getHeader();
        createNode(writer, "title", header.getTitle());
        createNode(writer, "link", header.getLink());
        createNode(writer, "description", header.getDescription());
        createNode(writer, "language", header.getLanguage());
        createNode(writer, "copyright", header.getCopyright());
        createNode(writer, "pubDate", header.getPubDate());
        for (RSSEntry entry : rssfeed.getEntries()) {
            writer.add(eventFactory.createStartElement("", "", "item"));
            createNode(writer, "title", entry.getTitle());
            createNode(writer, "description", entry.getDescription() == null ? "" : entry.getDescription());
            createNode(writer, "link", entry.getLink());
            createNode(writer, "guid", entry.getGuid());
            createNode(writer, "pubDate", entry.getPubDate());
            writer.add(eventFactory.createEndElement("", "", "item"));
        }

        writer.add(eventFactory.createEndElement("", "", "channel"));
        writer.add(eventFactory.createEndElement("", "", "rss"));

        writer.add(eventFactory.createEndDocument());
        writer.close();
    }

    private static void createNode(XMLEventWriter eventWriter, String name, String value) throws XMLStreamException {
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();

        StartElement sElement = eventFactory.createStartElement("", "", name);
        eventWriter.add(sElement);

        Characters characters = eventFactory.createCharacters(value);
        eventWriter.add(characters);

        EndElement eElement = eventFactory.createEndElement("", "", name);
        eventWriter.add(eElement);
    }
}
