//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2014.11.23 at 04:49:45 PM EST 
//


package com.tesora.dve.sql.raw.jaxb;

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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DynamicPersistentGroupType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DynamicPersistentGroupType">
 *   &lt;complexContent>
 *     &lt;extension base="{}GroupType">
 *       &lt;attribute name="sites" use="required" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="persistentgroup" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DynamicPersistentGroupType")
public class DynamicPersistentGroupType
    extends GroupType
{

    @XmlAttribute(name = "sites", required = true)
    protected int sites;
    @XmlAttribute(name = "persistentgroup", required = true)
    protected String persistentgroup;

    /**
     * Gets the value of the sites property.
     * 
     */
    public int getSites() {
        return sites;
    }

    /**
     * Sets the value of the sites property.
     * 
     */
    public void setSites(int value) {
        this.sites = value;
    }

    /**
     * Gets the value of the persistentgroup property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPersistentgroup() {
        return persistentgroup;
    }

    /**
     * Sets the value of the persistentgroup property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPersistentgroup(String value) {
        this.persistentgroup = value;
    }

}
