//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.11.01 at 06:28:31 PM EDT 
//


package com.tesora.dve.tools.analyzer.jaxb;

/*
 * #%L
 * Tesora Inc.
 * Database Virtualization Engine
 * %%
 * Copyright (C) 2011 - 2014 Tesora Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="analyzer" type="{}AnalyzerType"/>
 *         &lt;element name="databaseInformation" type="{}DatabaseInformationType"/>
 *         &lt;element name="databases" type="{}DatabasesType"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "analyzer",
    "databaseInformation",
    "databases"
})
@XmlRootElement(name = "dbAnalyzerReport")
public class DbAnalyzerReport {

    @XmlElement(required = true)
    protected AnalyzerType analyzer;
    @XmlElement(required = true)
    protected DatabaseInformationType databaseInformation;
    @XmlElement(required = true)
    protected DatabasesType databases;

    /**
     * Gets the value of the analyzer property.
     * 
     * @return
     *     possible object is
     *     {@link AnalyzerType }
     *     
     */
    public AnalyzerType getAnalyzer() {
        return analyzer;
    }

    /**
     * Sets the value of the analyzer property.
     * 
     * @param value
     *     allowed object is
     *     {@link AnalyzerType }
     *     
     */
    public void setAnalyzer(AnalyzerType value) {
        this.analyzer = value;
    }

    /**
     * Gets the value of the databaseInformation property.
     * 
     * @return
     *     possible object is
     *     {@link DatabaseInformationType }
     *     
     */
    public DatabaseInformationType getDatabaseInformation() {
        return databaseInformation;
    }

    /**
     * Sets the value of the databaseInformation property.
     * 
     * @param value
     *     allowed object is
     *     {@link DatabaseInformationType }
     *     
     */
    public void setDatabaseInformation(DatabaseInformationType value) {
        this.databaseInformation = value;
    }

    /**
     * Gets the value of the databases property.
     * 
     * @return
     *     possible object is
     *     {@link DatabasesType }
     *     
     */
    public DatabasesType getDatabases() {
        return databases;
    }

    /**
     * Sets the value of the databases property.
     * 
     * @param value
     *     allowed object is
     *     {@link DatabasesType }
     *     
     */
    public void setDatabases(DatabasesType value) {
        this.databases = value;
    }

}
