//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.record.persistence.genericrecordstore;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.deegree.commons.types.datetime.Date;
import org.deegree.commons.xml.NamespaceContext;
import org.deegree.commons.xml.XMLAdapter;
import org.deegree.commons.xml.XPath;
import org.deegree.crs.CRS;
import org.deegree.protocol.csw.CSWConstants;

/**
 * The parsing for the ISO application profile.
 * 
 * @author <a href="mailto:thomas@lat-lon.de">Steffen Thomas</a>
 * @author last edited by: $Author: thomas $
 * 
 * @version $Revision: $, $Date: $
 */
public class ISOQPParsing extends XMLAdapter {

    private NamespaceContext nsContext = new NamespaceContext( XMLAdapter.nsContext );

    private final static String CSW_PREFIX = "csw";

    /**
     * Tablename in backend
     */
    private final static String RECORDBRIEF = "recordbrief";

    /**
     * Tablename in backend
     */
    private final static String RECORDSUMMARY = "recordsummary";

    /**
     * Tablename in backend
     */
    private final static String RECORDFULL = "recordfull";

    /**
     * XML element name in the representation of the response
     */
    private final static String BRIEFRECORD = "BriefRecord";

    /**
     * XML element name in the representation of the response
     */
    private final static String SUMMARYRECORD = "SummaryRecord";

    /**
     * XML element name in the representation of the response
     */
    private final static String RECORD = "Record";

    static Map<String, String> tableRecordType = new HashMap<String, String>();

    static {

        tableRecordType.put( RECORDBRIEF, BRIEFRECORD );
        tableRecordType.put( RECORDSUMMARY, SUMMARYRECORD );
        tableRecordType.put( RECORDFULL, RECORD );
    }

    QueryableProperties qp = new QueryableProperties();

    ReturnableProperties rp = new ReturnableProperties();

    private OMFactory factory = OMAbstractFactory.getOMFactory();

    private OMNamespace namespaceCSW = factory.createOMNamespace( "http://www.opengis.net/cat/csw/2.0.2", "csw" );

    private OMNamespace namespaceDC = factory.createOMNamespace( "http://purl.org/dc/elements/1.1/", "dc" );

    private OMNamespace namespaceDCT = factory.createOMNamespace( "http://purl.org/dc/terms/", "dct" );

    private OMNamespace namespaceGMD = factory.createOMNamespace( "http://www.isotc211.org/2005/gmd", "" );

    private OMNamespace namespaceOWS = factory.createOMNamespace( "http://www.opengis.net/ows", "ows" );

    private int id;

    private Connection connection;

    private OMElement elementFull;

    private OMElement identifier = null;

    private OMElement hierarchyLevel = null;

    private OMElement hierarchyLevelName = null;

    private OMElement language = null;

    private OMElement dataQualityInfo = null;

    private OMElement characterSet = null;

    private OMElement metadataStandardName = null;

    private OMElement metadataStandardVersion = null;

    private OMElement parentIdentifier = null;

    private OMElement identificationInfo = null;

    private OMElement referenceSystemInfo = null;

    private OMElement distributionInfo = null;

    private Statement stm;

    private List<Integer> recordInsertIDs;

    /**
     * 
     * 
     * @param element
     * @param connection
     */
    public ISOQPParsing( OMElement element, Connection connection ) {

        this.elementFull = element.cloneOMElement();
        this.connection = connection;

        setRootElement( element );
        nsContext.addNamespace( rootElement.getDefaultNamespace().getPrefix(),
                                rootElement.getDefaultNamespace().getNamespaceURI() );
        nsContext.addNamespace( CSW_PREFIX, CSWConstants.CSW_202_NS );
        nsContext.addNamespace( "srv", "http://www.isotc211.org/2005/srv" );

        try {

            parseAPISO( element );

        } catch ( IOException e ) {

            e.printStackTrace();
        }
    }

    /**
     * Parses the recordelement that should be inserted into the backend. Every elementknot is put into an OMElement and
     * its atomic representation:
     * <p>
     * e.g. the "fileIdentifier" is put into an OMElement identifier and its identification-String is put into the
     * {@link QueryableProperties}.
     * 
     * @param element
     *            the record that should be inserted into the backend
     * @throws IOException
     */
    private void parseAPISO( OMElement element )
                            throws IOException {
        qp.setAnyText( element.toString() );

        // /*/self::node() => /*/self::* => /* -> Transaction
        // */MD_Metadata -> null
        // //MD_Metadata => ./gmd:MD_Metadata -> null
        // */self::* => * => ./* => ./child::*-> fileIdentifier, identificationInfo
        // ./. => ./self::node() -> MD_Metadata
        // //. -> jedes element...alles also
        // /*/*/gmd:MD_Metadata -> MD_Metadata
        List<OMElement> recordElements = getElements( rootElement, new XPath( "*", nsContext ) );
        for ( OMElement elem : recordElements ) {

            if ( elem.getLocalName().equals( "fileIdentifier" ) ) {
                qp.setIdentifier( getNodeAsString( elem, new XPath( "./gco:CharacterString", nsContext ), null ) );

                identifier = elem;
                OMNamespace namespace = identifier.getNamespace();
                identifier.setNamespace( namespace );

                continue;

            }
            if ( elem.getLocalName().equals( "hierarchyLevel" ) ) {
                String type = getNodeAsString( elem, new XPath( "./gmd:MD_ScopeCode/@codeListValue", nsContext ),
                                               "dataset" );

                qp.setType( type );

                hierarchyLevel = elem;
                OMNamespace namespace = hierarchyLevel.getNamespace();
                hierarchyLevel.setNamespace( namespace );

                continue;
            }

            if ( elem.getLocalName().equals( "hierarchyLevelName" ) ) {

                hierarchyLevelName = elem;
                OMNamespace namespace = hierarchyLevelName.getNamespace();
                hierarchyLevelName.setNamespace( namespace );

                continue;
            }

            if ( elem.getLocalName().equals( "parentIdentifier" ) ) {
                qp.setParentIdentifier( getNodeAsString( elem, new XPath( "./gco:CharacterString", nsContext ), null ) );

                parentIdentifier = elem;
                OMNamespace namespace = parentIdentifier.getNamespace();
                parentIdentifier.setNamespace( namespace );

                continue;

            }

            if ( elem.getLocalName().equals( "dateStamp" ) ) {

                String dateString = getNodeAsString( elem, new XPath( "./gco:Date", nsContext ), "0000-00-00" );
                Date date = null;
                try {
                    date = new Date( dateString );
                } catch ( ParseException e ) {

                    e.printStackTrace();
                }

                qp.setModified( date );
                // String[] dateStrings = getNodesAsStrings( elem, new XPath( "./gco:Date", nsContext ) );
                // Date[] dates = new Date[dateStrings.length];
                // Date date = null;
                // for ( int i = 0; i < dateStrings.length; i++ ) {
                // try {
                // date = new Date( dateStrings[i] );
                // } catch ( ParseException e ) {
                //
                // e.printStackTrace();
                // }
                // dates[i] = date;
                //
                // }
                //
                // qp.setModified( Arrays.asList( dates ) );
                continue;
            }

            // TODO there are more than one refSysInfo!!
            if ( elem.getLocalName().equals( "referenceSystemInfo" ) ) {
                OMElement crsElement = getElement(
                                                   elem,
                                                   new XPath(
                                                              "./gmd:MD_ReferenceSystem/gmd:referenceSystemIdentifier/gmd:RS_Identifier",
                                                              nsContext ) );
                String crsIdentification = getNodeAsString( crsElement, new XPath( "./gmd:code/gco:CharacterString",
                                                                                   nsContext ), "" );

                String crsAuthority = getNodeAsString( crsElement, new XPath( "./gmd:codeSpace/gco:CharacterString",
                                                                              nsContext ), "" );

                String crsVersion = getNodeAsString( crsElement, new XPath( "./gmd:version/gco:CharacterString",
                                                                            nsContext ), "" );

                // CRS crs = new CRS( crsAuthority, crsIdentification, crsVersion );
                CRS crs = new CRS( crsIdentification );
                qp.setCrs( crs );
                referenceSystemInfo = elem;
                OMNamespace namespace = referenceSystemInfo.getNamespace();
                referenceSystemInfo.setNamespace( namespace );

                continue;

            }

            if ( elem.getLocalName().equals( "language" ) ) {

                rp.setLanguage( getNodeAsString( elem, new XPath( "./gco:CharacterString", nsContext ), null ) );
                language = elem;
                continue;
            }

            if ( elem.getLocalName().equals( "dataQualityInfo" ) ) {

                dataQualityInfo = elem;

                continue;
            }

            if ( elem.getLocalName().equals( "characterSet" ) ) {

                characterSet = elem;
                continue;
            }

            if ( elem.getLocalName().equals( "metadataStandardName" ) ) {

                metadataStandardName = elem;
                continue;
            }

            if ( elem.getLocalName().equals( "metadataStandardVersion" ) ) {

                metadataStandardVersion = elem;
                continue;
            }
            if ( elem.getLocalName().equals( "parentIdentifier" ) ) {

                parentIdentifier = elem;
                qp.setParentIdentifier( getNodeAsString( elem, new XPath( "./gco:CharacterString", nsContext ), null ) );
                continue;
            }

            if ( elem.getLocalName().equals( "identificationInfo" ) ) {

                OMElement md_identification = getElement( elem, new XPath( "./gmd:MD_Identification", nsContext ) );

                OMElement md_dataIdentification = getElement( elem,
                                                              new XPath( "./gmd:MD_DataIdentification", nsContext ) );

                OMElement ci_responsibleParty = getElement( md_dataIdentification,
                                                            new XPath( "./gmd:pointOfContact/gmd:CI_ResponsibleParty",
                                                                       nsContext ) );

                String resourceLanguage = getNodeAsString(
                                                           md_dataIdentification,
                                                           new XPath( "./gmd:language/gco:CharacterString", nsContext ),
                                                           null );
                qp.setResourceLanguage( resourceLanguage );

                String creator = getNodeAsString(
                                                  ci_responsibleParty,
                                                  new XPath(
                                                             "./gmd:organisationName[../gmd:role/gmd:CI_RoleCode/@codeListValue='originator']/gco:CharacterString",
                                                             nsContext ), null );

                rp.setCreator( creator );

                String publisher = getNodeAsString(
                                                    ci_responsibleParty,
                                                    new XPath(
                                                               "./gmd:organisationName[../gmd:role/gmd:CI_RoleCode/@codeListValue='publisher']/gco:CharacterString",
                                                               nsContext ), null );

                rp.setPublisher( publisher );

                String contributor = getNodeAsString(
                                                      ci_responsibleParty,
                                                      new XPath(
                                                                 "./gmd:organisationName[../gmd:role/gmd:CI_RoleCode/@codeListValue='author']/gco:CharacterString",
                                                                 nsContext ), null );
                rp.setContributor( contributor );

                String[] rightsElements = getNodesAsStrings(
                                                             md_dataIdentification,
                                                             new XPath(
                                                                        "./gmd:resourceConstraints/gmd:MD_LegalConstraints/gmd:accessConstraints/@codeListValue",
                                                                        nsContext ) );
                rp.setRights( Arrays.asList( rightsElements ) );

                // OMElement sv_serviceIdentification = getElement( elem, new XPath( "./srv:SV_ServiceIdentification",
                // nsContext ) );

                // String couplingType = getNodeAsString( sv_serviceIdentification, new XPath(
                // "./srv:couplingType/srv:SV_CouplingType/@codeListValue", nsContext ), null );

                OMElement _abstract = getElement( md_dataIdentification, new XPath( "./gmd:abstract", nsContext ) );

                OMElement bbox = getElement(
                                             md_dataIdentification,
                                             new XPath(
                                                        "./gmd:extent/gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox",
                                                        nsContext ) );

                List<OMElement> descriptiveKeywords = getElements( md_dataIdentification,
                                                                   new XPath( "./gmd:descriptiveKeywords", nsContext ) );

                String[] topicCategories = getNodesAsStrings(
                                                              md_dataIdentification,
                                                              new XPath(
                                                                         "./gmd:topicCategory/gmd:MD_TopicCategoryCode",
                                                                         nsContext ) );

                String graphicOverview = getNodeAsString( md_dataIdentification,
                                                          new XPath( "./gmd:graphicOverview/gmd:MD_BrowseGraphic",
                                                                     nsContext ), null );

                String[] titleElements = getNodesAsStrings(
                                                            md_dataIdentification,
                                                            new XPath(
                                                                       "./gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString",
                                                                       nsContext ) );

                String[] alternateTitleElements = getNodesAsStrings(
                                                                     md_dataIdentification,
                                                                     new XPath(
                                                                                "./gmd:citation/gmd:CI_Citation/gmd:alternateTitle/gco:CharacterString",
                                                                                nsContext ) );

                double boundingBoxWestLongitude = getNodeAsDouble( bbox,
                                                                   new XPath( "./gmd:westBoundLongitude/gco:Decimal",
                                                                              nsContext ), 0.0 );

                double boundingBoxEastLongitude = getNodeAsDouble( bbox,
                                                                   new XPath( "./gmd:eastBoundLongitude/gco:Decimal",
                                                                              nsContext ), 0.0 );

                double boundingBoxSouthLatitude = getNodeAsDouble( bbox,
                                                                   new XPath( "./gmd:southBoundLatitude/gco:Decimal",
                                                                              nsContext ), 0.0 );

                double boundingBoxNorthLatitude = getNodeAsDouble( bbox,
                                                                   new XPath( "./gmd:northBoundLatitude/gco:Decimal",
                                                                              nsContext ), 0.0 );

                qp.setBoundingBox( new BoundingBox( boundingBoxWestLongitude, boundingBoxEastLongitude,
                                                    boundingBoxSouthLatitude, boundingBoxNorthLatitude ) );

                qp.setTitle( Arrays.asList( titleElements ) );

                qp.setAlternateTitle( Arrays.asList( alternateTitleElements ) );

                // not necessary actually...
                rp.setGraphicOverview( graphicOverview );
                // TODO same with serviceType and serviceTypeVersion
                Keyword keywordClass;

                List<Keyword> listOfKeywords = new ArrayList<Keyword>();
                for ( OMElement md_keywords : descriptiveKeywords ) {
                    keywordClass = new Keyword();
                    // keywordClass =
                    String keywordType = getNodeAsString(
                                                          md_keywords,
                                                          new XPath(
                                                                     "./gmd:MD_Keywords/gmd:type/gmd:MD_KeywordTypeCode/@codeListValue",
                                                                     nsContext ), null );

                    String[] keywords = getNodesAsStrings(
                                                           md_keywords,
                                                           new XPath(
                                                                      "./gmd:MD_Keywords/gmd:keyword/gco:CharacterString",
                                                                      nsContext ) );

                    String thesaurus = getNodeAsString(
                                                        md_keywords,
                                                        new XPath(
                                                                   "./gmd:MD_Keywords/gmd:thesaurusName/gmd:CI_Citation/gmd:title/gco:CharacterString",
                                                                   nsContext ), null );

                    keywordClass.setKeywordType( keywordType );

                    keywordClass.setKeywords( Arrays.asList( keywords ) );

                    keywordClass.setThesaurus( thesaurus );
                    listOfKeywords.add( keywordClass );

                }

                qp.setKeywords( listOfKeywords );
                qp.setTopicCategory( Arrays.asList( topicCategories ) );

                // TODO be aware of Dateincompatibilities and read them from the right knot revisionDate
                String revisionDateString = getNodeAsString( elem, new XPath( "./gco:Date", nsContext ), "0000-00-00" );
                Date date = null;
                try {
                    date = new Date( revisionDateString );
                } catch ( ParseException e ) {

                    e.printStackTrace();
                }

                qp.setRevisionDate( date );

                // TODO be aware of Dateincompatibilities and read them from the right knot creationDate
                String creationDateString = getNodeAsString( elem, new XPath( "./gco:Date", nsContext ), "0000-00-00" );

                try {
                    date = new Date( creationDateString );
                } catch ( ParseException e ) {

                    e.printStackTrace();
                }

                qp.setCreationDate( date );

                // TODO be aware of Dateincompatibilities and read them from the right knot publicationDate
                String publicationDateString = getNodeAsString( elem, new XPath( "./gco:Date", nsContext ),
                                                                "0000-00-00" );

                try {
                    date = new Date( publicationDateString );
                } catch ( ParseException e ) {

                    e.printStackTrace();
                }

                qp.setPublicationDate( date );

                // TODO maybe md_dataIdentification
                String relation = getNodeAsString( md_identification,
                                                   new XPath( "./gmd:aggreationInfo/gco:CharacterString", nsContext ),
                                                   null );
                rp.setRelation( relation );

                // TODO maybe md_dataIdentification
                String[] rightsStrings = getNodesAsStrings(
                                                            md_identification,
                                                            new XPath(
                                                                       "./gmd:resourceConstraints/gmd:MD_LegalConstraints/gmd:accessConstraints/@codeListValue",
                                                                       nsContext ) );

                rp.setRights( Arrays.asList( rightsStrings ) );

                // TODO RevisionDate

                String organisationName = getNodeAsString(
                                                           md_identification,
                                                           new XPath(
                                                                      "./gmd:pointOfContact/gmd:CI_ResponsibleParty/gmd:organisationName/gco:CharacterString",
                                                                      nsContext ), null );

                qp.setOrganisationName( organisationName );

                boolean hasSecurityConstraint = getNodeAsBoolean(
                                                                  md_identification,
                                                                  new XPath(
                                                                             "./gmd:MD_Identification/gmd:resourceConstraints/gmd:MD_SecurityConstraints",
                                                                             nsContext ), false );
                qp.setHasSecurityConstraints( hasSecurityConstraint );

                String[] _abstractStrings = getNodesAsStrings( _abstract,
                                                               new XPath( "./gco:CharacterString", nsContext ) );

                qp.set_abstract( Arrays.asList( _abstractStrings ) );

                identificationInfo = elem;
                OMNamespace namespace = identificationInfo.getNamespace();
                identificationInfo.setNamespace( namespace );
                continue;

            }

            if ( elem.getLocalName().equals( "distributionInfo" ) ) {
                List<OMElement> formats = getElements(
                                                       elem,
                                                       new XPath(
                                                                  "./gmd:MD_Distribution/gmd:distributionFormat/gmd:MD_Format",
                                                                  nsContext ) );

                Format formatClass = null;
                List<Format> listOfFormats = new ArrayList<Format>();
                for ( OMElement md_format : formats ) {
                    formatClass = new Format();
                    String formatName = getNodeAsString( md_format, new XPath( "./gmd:name/gco:CharacterString",
                                                                               nsContext ), null );

                    String formatVersion = getNodeAsString( md_format, new XPath( "./gmd:version/gco:CharacterString",
                                                                                  nsContext ), null );

                    formatClass.setName( formatName );
                    formatClass.setVersion( formatVersion );

                    listOfFormats.add( formatClass );

                }

                qp.setFormat( listOfFormats );

                continue;
            }

            if ( elem.getLocalName().equals( "distributionInfo" ) ) {
                distributionInfo = elem;
                continue;

            }

        }

    }

    /**
     * This method executes the statement for INSERT datasets
     * <p>
     * TODO ExceptionHandling if there are properties that have to be in the insert statement
     * 
     * @throws IOException
     */
    public void executeInsertStatement()
                            throws IOException {
        try {
            stm = connection.createStatement();

            boolean isUpdate = false;

            generateMainDatabaseDataset();
            generateISO();

            if ( qp.getTitle() != null ) {
                generateISOQP_TitleStatement( isUpdate );
            }
            if ( qp.getType() != null ) {
                generateISOQP_TypeStatement( isUpdate );
            }
            if ( qp.getKeywords() != null ) {
                generateISOQP_KeywordStatement( isUpdate );
            }
            if ( qp.getTopicCategory() != null ) {
                generateISOQP_TopicCategoryStatement( isUpdate );
            }
            if ( qp.getFormat() != null ) {
                generateISOQP_FormatStatement( isUpdate );
            }
            // TODO relation
            if ( qp.get_abstract() != null ) {
                generateISOQP_AbstractStatement( isUpdate );
            }
            if ( qp.getAlternateTitle() != null ) {
                generateISOQP_AlternateTitleStatement( isUpdate );
            }
            if ( qp.getResourceIdentifier() != null ) {
                generateISOQP_ResourceIdentifierStatement( isUpdate );
            }
            if ( qp.getOrganisationName() != null ) {
                generateISOQP_OrganisationNameStatement( isUpdate );
            }
            // TODO spatial
            if ( qp.getBoundingBox() != null ) {
                generateISOQP_BoundingBoxStatement( isUpdate );
            }

            stm.close();
        } catch ( SQLException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * This method executes the statement for updating the queryable- and returnable properties of one record.
     * 
     */
    public void executeUpdateStatement() {
        final String databaseTable = "datasets";
        boolean isUpdate = true;

        StringWriter sqlStatementUpdate = new StringWriter( 500 );
        StringBuffer buf = new StringBuffer();
        int requestedId = 0;
        String modifiedAttribute = "null";
        try {
            stm = connection.createStatement();
            sqlStatementUpdate.append( "SELECT " + databaseTable + ".id from " + databaseTable + " where "
                                       + databaseTable + ".identifier = '" + qp.getIdentifier() + "'" );
            System.out.println( sqlStatementUpdate.toString() );
            buf = sqlStatementUpdate.getBuffer();
            ResultSet rs = connection.createStatement().executeQuery( sqlStatementUpdate.toString() );

            if ( rs != null ) {
                while ( rs.next() ) {
                    requestedId = rs.getInt( 1 );
                    System.out.println( rs.getInt( 1 ) );
                }
                buf.setLength( 0 );
                rs.close();
            }
            if ( requestedId != 0 ) {
                this.id = requestedId;

                if ( qp.getModified() == null || qp.getModified().equals( new Date( "0000-00-00" ) ) ) {
                } else {
                    modifiedAttribute = "'" + qp.getModified() + "'";
                }

                // TODO version

                // TODO status

                // anyText
                if ( qp.getAnyText() != null ) {

                    sqlStatementUpdate.write( "UPDATE " + databaseTable + " SET anyText = '" + qp.getAnyText()
                                              + "' WHERE id = " + requestedId );

                    buf = sqlStatementUpdate.getBuffer();
                    System.out.println( sqlStatementUpdate.toString() );
                    stm.executeUpdate( sqlStatementUpdate.toString() );
                    buf.setLength( 0 );

                }
                // identifier
                if ( qp.getIdentifier() != null ) {

                    sqlStatementUpdate.write( "UPDATE " + databaseTable + " SET identifier = '" + qp.getIdentifier()
                                              + "' WHERE id = " + requestedId );

                    buf = sqlStatementUpdate.getBuffer();
                    System.out.println( sqlStatementUpdate.toString() );
                    stm.executeUpdate( sqlStatementUpdate.toString() );
                    buf.setLength( 0 );
                }
                // modified
                if ( qp.getModified() != null || !qp.getModified().equals( new Date( "0000-00-00" ) ) ) {
                    sqlStatementUpdate.write( "UPDATE " + databaseTable + " SET modified = " + modifiedAttribute
                                              + " WHERE id = " + requestedId );
                    buf = sqlStatementUpdate.getBuffer();
                    System.out.println( sqlStatementUpdate.toString() );
                    stm.executeUpdate( sqlStatementUpdate.toString() );
                    buf.setLength( 0 );
                }
                // hassecurityconstraints
                if ( qp.isHasSecurityConstraints() == true ) {
                    sqlStatementUpdate.write( "UPDATE " + databaseTable + " SET hassecurityconstraints = '"
                                              + qp.isHasSecurityConstraints() + "' WHERE id = " + requestedId );

                    buf = sqlStatementUpdate.getBuffer();
                    System.out.println( sqlStatementUpdate.toString() );
                    stm.executeUpdate( sqlStatementUpdate.toString() );
                    buf.setLength( 0 );
                }

                // language
                if ( rp.getLanguage() != null ) {
                    sqlStatementUpdate.write( "UPDATE " + databaseTable + " SET language = '" + rp.getLanguage()
                                              + "' WHERE id = " + requestedId );

                    buf = sqlStatementUpdate.getBuffer();
                    System.out.println( sqlStatementUpdate.toString() );
                    stm.executeUpdate( sqlStatementUpdate.toString() );
                    buf.setLength( 0 );
                }
                // parentidentifier
                if ( qp.getParentIdentifier() != null ) {
                    sqlStatementUpdate.write( "UPDATE " + databaseTable + " SET parentidentifier = '"
                                              + qp.getParentIdentifier() + "' WHERE id = " + requestedId );

                    buf = sqlStatementUpdate.getBuffer();
                    System.out.println( sqlStatementUpdate.toString() );
                    stm.executeUpdate( sqlStatementUpdate.toString() );
                    buf.setLength( 0 );
                }
                // TODO source

                // TODO association

                // recordBrief, recordSummary, recordFull update
                updateRecord( requestedId );

                if ( qp.getTitle() != null ) {
                    generateISOQP_TitleStatement( isUpdate );
                }
                if ( qp.getType() != null ) {
                    generateISOQP_TypeStatement( isUpdate );
                }
                if ( qp.getKeywords() != null ) {
                    generateISOQP_KeywordStatement( isUpdate );
                }
                if(qp.getTopicCategory() != null){
                    generateISOQP_TopicCategoryStatement( isUpdate );
                }
                if ( qp.getFormat() != null ) {
                    generateISOQP_FormatStatement( isUpdate );
                }
                if ( qp.get_abstract() != null ) {
                    generateISOQP_AbstractStatement( isUpdate );
                }
                if ( qp.getAlternateTitle() != null ) {
                    generateISOQP_AlternateTitleStatement( isUpdate );
                }
                if ( qp.getResourceIdentifier() != null ) {
                    generateISOQP_ResourceIdentifierStatement( isUpdate );
                }
                if ( qp.getOrganisationName() != null ) {
                    generateISOQP_OrganisationNameStatement( isUpdate );
                }
                if ( qp.getBoundingBox() != null ) {
                    generateISOQP_BoundingBoxStatement( isUpdate );
                }
            } else {
                // TODO think about what response should be written if there is no such dataset in the backend??
                String msg = "No dataset found for the identifier --> " + qp.getIdentifier() + " <--. ";
                throw new SQLException( msg );
            }

            stm.close();
        } catch ( SQLException e ) {

            e.printStackTrace();
        } catch ( IOException e ) {

            e.printStackTrace();
        } catch ( ParseException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * BE AWARE: the "modified" attribute is get from the first position in the list. The backend has the possibility to
     * add one such attribute. In the xsd-file there are more possible...
     * 
     */
    private void generateMainDatabaseDataset() {
        final String databaseTable = "datasets";
        String sqlStatement = "";
        String modifiedAttribute = "null";
        try {

            this.id = getLastDatasetId( connection, databaseTable );
            this.id++;

            if ( qp.getModified() == null || qp.getModified().equals( new Date( "0000-00-00" ) ) ) {
            } else {
                modifiedAttribute = "'" + qp.getModified() + "'";
            }

            sqlStatement += "INSERT INTO "
                            + databaseTable
                            + " (id, version, status, anyText, identifier, modified, hassecurityconstraints, language, parentidentifier, source, association) VALUES ("
                            + this.id + ",null,null,'" + qp.getAnyText() + "','" + qp.getIdentifier() + "',"
                            + modifiedAttribute + "," + qp.isHasSecurityConstraints() + ",'" + rp.getLanguage() + "','"
                            + qp.getParentIdentifier() + "','', null);";
            System.out.println( sqlStatement );
            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        } catch ( ParseException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * Updating the XML representation of a record in DC and ISO.
     * 
     * @param fk_datasets
     *            which record dataset should be updated
     * @throws IOException
     */
    private void updateRecord( int fk_datasets )
                            throws IOException {

        String isoOMElement = "";

        for ( String databaseTable : tableRecordType.keySet() ) {

            StringWriter sqlStatement = new StringWriter( 500 );
            StringBuffer buf = new StringBuffer();
            OMElement omElement = null;

            try {
                // DC-update
                omElement = factory.createOMElement( tableRecordType.get( databaseTable ), namespaceCSW );

                if ( omElement.getLocalName().equals( BRIEFRECORD ) ) {
                    setDCBriefElements( omElement );
                    isoOMElement = setISOBriefElements().toString();
                } else if ( omElement.getLocalName().equals( SUMMARYRECORD ) ) {
                    setDCSummaryElements( omElement );
                    isoOMElement = setISOSummaryElements().toString();
                } else {
                    setDCFullElements( omElement );
                    isoOMElement = elementFull.toString();
                }

                setBoundingBoxElement( omElement );

                sqlStatement.write( "UPDATE " + databaseTable + " SET data = '" + omElement.toString()
                                    + "' WHERE fk_datasets = " + fk_datasets + " AND format = " + 1 );

                buf = sqlStatement.getBuffer();
                stm.executeUpdate( sqlStatement.toString() );
                buf.setLength( 0 );

                // ISO-update
                sqlStatement.write( "UPDATE " + databaseTable + " SET data = '" + isoOMElement
                                    + "' WHERE fk_datasets = " + fk_datasets + " AND format = " + 2 );

                buf = sqlStatement.getBuffer();
                stm.executeUpdate( sqlStatement.toString() );
                buf.setLength( 0 );

            } catch ( SQLException e ) {

                e.printStackTrace();
            }
        }

    }

    /**
     * Generates the ISO representation in brief, summary and full for this dataset.
     * 
     * @throws IOException
     */
    private void generateISO()
                            throws IOException {

        String sqlStatement = "";
        String isoElements = "";
        int fk_datasets = this.id;
        int idDatabaseTable = 0;
        for ( String databaseTable : tableRecordType.keySet() ) {

            if ( databaseTable.equals( RECORDBRIEF ) ) {
                isoElements = setISOBriefElements().toString();
            } else if ( databaseTable.equals( RECORDSUMMARY ) ) {
                isoElements = setISOSummaryElements().toString();
            } else {
                isoElements = elementFull.toString();
            }

            try {

                idDatabaseTable = getLastDatasetId( connection, databaseTable );
                idDatabaseTable++;

                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, format, data) VALUES ("
                               + idDatabaseTable + "," + fk_datasets + ", 2, '" + isoElements + "');";

                stm.executeUpdate( sqlStatement );

            } catch ( SQLException e ) {

                e.printStackTrace();
            }
            generateDC( databaseTable );
        }

    }

    /**
     * Generates the Dublin Core representation in brief, summary and full for this dataset.
     * 
     * @param databaseTable
     */
    private void generateDC( String databaseTable ) {
        OMElement omElement = null;
        String sqlStatement = "";

        int fk_datasets = this.id;

        int idDatabaseTable = 0;

        try {
            recordInsertIDs = new ArrayList<Integer>();

            idDatabaseTable = getLastDatasetId( connection, databaseTable );
            idDatabaseTable++;

            omElement = factory.createOMElement( tableRecordType.get( databaseTable ), namespaceCSW );

            if ( omElement.getLocalName().equals( BRIEFRECORD ) ) {
                setDCBriefElements( omElement );
            } else if ( omElement.getLocalName().equals( SUMMARYRECORD ) ) {
                setDCSummaryElements( omElement );
            } else {
                setDCFullElements( omElement );
            }

            setBoundingBoxElement( omElement );

            sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, format, data) VALUES ("
                           + idDatabaseTable + "," + fk_datasets + ", 1, '" + omElement.toString() + "');";

            recordInsertIDs.add( idDatabaseTable );

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the organisationname for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_OrganisationNameStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_organisationname";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, organisationname) VALUES (" + id
                               + "," + mainDatabaseTableID + ",'" + qp.getResourceIdentifier() + "');";
            } else {
                sqlStatement = "UPDATE " + databaseTable + " SET organisationname = '" + qp.getResourceIdentifier()
                               + "' WHERE fk_datasets = " + mainDatabaseTableID + ";";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the revisiondate for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_RevisionDateStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_revisiondate";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        String revisionDateAttribute = "null";
        int id = 0;
        try {

            if ( qp.getModified() == null || qp.getModified().equals( new Date( "0000-00-00" ) ) ) {
            } else {
                revisionDateAttribute = "'" + qp.getRevisionDate() + "'";
            }

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, revisiondate) VALUES (" + id + ","
                               + mainDatabaseTableID + ",'" + revisionDateAttribute + "');";
            } else {
                sqlStatement = "UPDATE " + databaseTable + " SET revisiondate = " + revisionDateAttribute
                               + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        } catch ( ParseException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the creationdate for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_CreationDateStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_creationdate";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        String creationDateAttribute = "null";
        int id = 0;
        try {

            if ( qp.getModified() == null || qp.getModified().equals( new Date( "0000-00-00" ) ) ) {
            } else {
                creationDateAttribute = "'" + qp.getCreationDate() + "'";
            }

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, creationdate) VALUES (" + id + ","
                               + mainDatabaseTableID + ",'" + creationDateAttribute + "');";
            } else {
                sqlStatement = "UPDATE " + databaseTable + " SET creationdate = " + creationDateAttribute
                               + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        } catch ( ParseException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * Generates the publicationdate for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_PublicationDateStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_publicationdate";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        String publicationDateAttribute = "null";
        int id = 0;
        try {

            if ( qp.getModified() == null || qp.getModified().equals( new Date( "0000-00-00" ) ) ) {
            } else {
                publicationDateAttribute = "'" + qp.getPublicationDate() + "'";
            }

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, publicationdate) VALUES (" + id
                               + "," + mainDatabaseTableID + "," + publicationDateAttribute + ");";
            } else {
                sqlStatement = "UPDATE " + databaseTable + " SET publicationdate = " + publicationDateAttribute
                               + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        } catch ( ParseException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * Generates the resourceidentifier for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_ResourceIdentifierStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_resourceidentifier";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, resourceidentifier) VALUES (" + id
                               + "," + mainDatabaseTableID + ",'" + qp.getResourceIdentifier() + "');";
            } else {
                sqlStatement = "UPDATE " + databaseTable + " SET resourceidentifier = '" + qp.getResourceIdentifier()
                               + "' WHERE fk_datasets = " + mainDatabaseTableID + ";";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the alternate title for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_AlternateTitleStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_alternatetitle";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == true ) {
                sqlStatement = "DELETE FROM " + databaseTable + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
                stm.executeUpdate( sqlStatement );
            }
            id = getLastDatasetId( connection, databaseTable );
            for ( String alternateTitle : qp.getAlternateTitle() ) {
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, alternatetitle) VALUES (" + id
                               + "," + mainDatabaseTableID + ",'" + alternateTitle + "');";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the title for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_TitleStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_title";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == true ) {
                sqlStatement = "DELETE FROM " + databaseTable + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
                stm.executeUpdate( sqlStatement );
            }
            id = getLastDatasetId( connection, databaseTable );
            for ( String title : qp.getTitle() ) {
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, title) VALUES (" + id + ","
                               + mainDatabaseTableID + ",'" + title + "');";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the type for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_TypeStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_type";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, type) VALUES (" + id + ","
                               + mainDatabaseTableID + ",'" + qp.getType() + "');";
            } else {
                sqlStatement = "UPDATE " + databaseTable + " SET type = '" + qp.getType() + "' WHERE fk_datasets = "
                               + mainDatabaseTableID + ";";
            }
            System.out.println( sqlStatement );
            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the keywords for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_KeywordStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_keyword";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == true ) {
                sqlStatement = "DELETE FROM " + databaseTable + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
                stm.executeUpdate( sqlStatement );
            }

            id = getLastDatasetId( connection, databaseTable );
            for ( Keyword keyword : qp.getKeywords() ) {
                for ( String keywordString : keyword.getKeywords() ) {

                    id++;
                    sqlStatement = "INSERT INTO " + databaseTable
                                   + " (id, fk_datasets, keywordtype, keyword, thesaurus) VALUES (" + id + ","
                                   + mainDatabaseTableID + ",'" + keyword.getKeywordType() + "','" + keywordString
                                   + "','" + keyword.getThesaurus() + "');";

                    stm.executeUpdate( sqlStatement );
                }
            }

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the topicCategory for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_TopicCategoryStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_topiccategory";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == true ) {
                sqlStatement = "DELETE FROM " + databaseTable + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
                stm.executeUpdate( sqlStatement );
            }

            id = getLastDatasetId( connection, databaseTable );
            for ( String topicCategory : qp.getTopicCategory() ) {

                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, topiccategory) VALUES (" + id + ","
                               + mainDatabaseTableID + ",'" + topicCategory + "');";

                stm.executeUpdate( sqlStatement );

            }

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the format for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_FormatStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_format";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == true ) {
                sqlStatement = "DELETE FROM " + databaseTable + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
                stm.executeUpdate( sqlStatement );
            }
            id = getLastDatasetId( connection, databaseTable );

            for ( Format format : qp.getFormat() ) {
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, format) VALUES (" + id + ","
                               + mainDatabaseTableID + ",'" + format.getName() + "');";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Generates the abstract for this dataset.
     * 
     * @param isUpdate
     */
    private void generateISOQP_AbstractStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_abstract";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == true ) {
                sqlStatement = "DELETE FROM " + databaseTable + " WHERE fk_datasets = " + mainDatabaseTableID + ";";
                stm.executeUpdate( sqlStatement );
            }
            id = getLastDatasetId( connection, databaseTable );
            for ( String _abstract : qp.get_abstract() ) {
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, abstract) VALUES (" + id + ","
                               + mainDatabaseTableID + ",'" + _abstract + "');";
            }

            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * 
     * @param isUpdate
     */
    // TODO one record got one or more bboxes?
    private void generateISOQP_BoundingBoxStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_boundingbox";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable + " (id, fk_datasets, bbox) VALUES (" + id + ","
                               + mainDatabaseTableID + ",SetSRID('BOX3D(" + qp.getBoundingBox().getEastBoundLongitude()
                               + " " + qp.getBoundingBox().getNorthBoundLatitude() + ","
                               + qp.getBoundingBox().getWestBoundLongitude() + " "
                               + qp.getBoundingBox().getSouthBoundLatitude() + ")'::box3d,4326));";
            } else {
                sqlStatement = "UPDATE " + databaseTable + " SET bbox = " + "SetSRID('BOX3D("
                               + qp.getBoundingBox().getEastBoundLongitude() + " "
                               + qp.getBoundingBox().getNorthBoundLatitude() + ","
                               + qp.getBoundingBox().getWestBoundLongitude() + " "
                               + qp.getBoundingBox().getSouthBoundLatitude() + ")'::box3d,4326) WHERE fk_datasets = "
                               + mainDatabaseTableID + ";";
            }
            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Creation of the CRS element.
     * 
     * @param isUpdate
     */
    // TODO think about one bbox has one crs? or not?
    private void generateISOQP_CRSStatement( boolean isUpdate ) {
        final String databaseTable = "isoqp_crs";
        String sqlStatement = "";
        int mainDatabaseTableID = this.id;
        int id = 0;
        try {

            if ( isUpdate == false ) {
                id = getLastDatasetId( connection, databaseTable );
                id++;
                sqlStatement = "INSERT INTO " + databaseTable
                               + " (id, fk_datasets, authority, id_crs, version) VALUES (" + id + ","
                               + mainDatabaseTableID + "," + qp.getCrs().getName() + "," + qp.getCrs().getName() + ","
                               + qp.getCrs().getName() + ");";
            } else {
                sqlStatement = "";
            }
            stm.executeUpdate( sqlStatement );

        } catch ( SQLException e ) {

            e.printStackTrace();
        }

    }

    /**
     * Provides the last known id in the databaseTable. So it is possible to insert new datasets into this table come
     * from this id.
     * 
     * @param conn
     * @param databaseTable
     *            the databaseTable that is requested.
     * @return the last Primary Key ID of the databaseTable.
     * @throws SQLException
     */
    private int getLastDatasetId( Connection conn, String databaseTable )
                            throws SQLException {
        int result = 0;
        String selectIDRows = "SELECT id from " + databaseTable + " ORDER BY id DESC LIMIT 1";
        ResultSet rsBrief = conn.createStatement().executeQuery( selectIDRows );

        while ( rsBrief.next() ) {

            result = rsBrief.getInt( 1 );

        }
        rsBrief.close();
        return result;

    }

    /**
     * @return the recordInsertIDs
     */
    public List<Integer> getRecordInsertIDs() {
        return recordInsertIDs;
    }

    /**
     * Creation of the "brief"-representation in DC of a record.
     * 
     * @param factory
     * @param omElement
     */
    private void setDCBriefElements( OMElement omElement ) {

        OMElement omIdentifier = factory.createOMElement( "identifier", namespaceDC );
        OMElement omType = factory.createOMElement( "type", namespaceDC );

        omIdentifier.setText( qp.getIdentifier() );

        omElement.addChild( omIdentifier );

        for ( String title : qp.getTitle() ) {
            OMElement omTitle = factory.createOMElement( "title", namespaceDC );
            omTitle.setText( title );
            omElement.addChild( omTitle );
        }
        if ( qp.getType() != null ) {
            omType.setText( qp.getType() );
        } else {
            omType.setText( "" );
        }
        omElement.addChild( omType );
    }

    /**
     * Creation of the "summary"-representation in DC of a record.
     * 
     * @param omElement
     */
    private void setDCSummaryElements( OMElement omElement ) {
        setDCBriefElements( omElement );
        
        
        OMElement omSubject = factory.createOMElement( "subject", namespaceDC );
        // dc:subject
        for ( Keyword subjects : qp.getKeywords() ) {
            for ( String subject : subjects.getKeywords() ) {

                
                omSubject.setText( subject );
                omElement.addChild( omSubject );
            }
        }
        for(String subject : qp.getTopicCategory()){
            omSubject.setText( subject );
            omElement.addChild( omSubject );
        }

        // dc:format
        if ( qp.getFormat() != null ) {
            for ( Format format : qp.getFormat() ) {
                OMElement omFormat = factory.createOMElement( "format", namespaceDC );
                omFormat.setText( format.getName() );
                omElement.addChild( omFormat );
            }
        } else {
            OMElement omFormat = factory.createOMElement( "format", namespaceDC );
            omElement.addChild( omFormat );
        }

        // dc:relation
        // TODO

        // dct:modified
        // for ( Date date : qp.getModified() ) {
        // OMElement omModified = factory.createOMElement( "modified", namespaceDCT );
        // omModified.setText( date.toString() );
        // omElement.addChild( omModified );
        // }
        OMElement omModified = factory.createOMElement( "modified", namespaceDCT );
        omElement.addChild( omModified );

        // dct:abstract
        for ( String _abstract : qp.get_abstract() ) {
            OMElement omAbstract = factory.createOMElement( "abstract", namespaceDCT );
            omAbstract.setText( _abstract.toString() );
            omElement.addChild( omAbstract );
        }

        // dct:spatial
        // TODO

    }

    /**
     * Creation of the "full"-representation in DC of a record.
     * 
     * @param omElement
     */
    private void setDCFullElements( OMElement omElement ) {

        setDCSummaryElements( omElement );

        OMElement omCreator = factory.createOMElement( "creator", namespaceDC );
        omCreator.setText( rp.getCreator() );
        omElement.addChild( omCreator );

        OMElement omPublisher = factory.createOMElement( "publisher", namespaceDC );
        omPublisher.setText( rp.getPublisher() );
        omElement.addChild( omPublisher );

        OMElement omContributor = factory.createOMElement( "contributor", namespaceDC );
        omContributor.setText( rp.getContributor() );
        omElement.addChild( omContributor );

        OMElement omSource = factory.createOMElement( "source", namespaceDC );
        omSource.setText( rp.getSource() );
        omElement.addChild( omSource );

        OMElement omLanguage = factory.createOMElement( "language", namespaceDC );
        omLanguage.setText( rp.getLanguage() );
        omElement.addChild( omLanguage );

        // dc:rights
        for ( String rights : rp.getRights() ) {
            OMElement omRights = factory.createOMElement( "rights", namespaceDC );
            omRights.setText( rights );
            omElement.addChild( omRights );
        }

    }

    /**
     * Creation of the boundingBox element. Specifies which points has to be at which corner.
     * 
     * @param omElement
     */
    private void setBoundingBoxElement( OMElement omElement ) {

        OMElement omBoundingBox = factory.createOMElement( "BoundingBox", namespaceOWS );
        OMElement omLowerCorner = factory.createOMElement( "LowerCorner", namespaceOWS );
        OMElement omUpperCorner = factory.createOMElement( "UpperCorner", namespaceOWS );
        // OMAttribute omCrs = factory.createOMAttribute( "crs", namespaceOWS, qp.getCrs() );
        System.out.println( "CRS: " + qp.getCrs() );

        omLowerCorner.setText( qp.getBoundingBox().getEastBoundLongitude() + " "
                               + qp.getBoundingBox().getSouthBoundLatitude() );
        omUpperCorner.setText( qp.getBoundingBox().getWestBoundLongitude() + " "
                               + qp.getBoundingBox().getNorthBoundLatitude() );
        omBoundingBox.addChild( omLowerCorner );
        omBoundingBox.addChild( omUpperCorner );
        if ( qp.getCrs() != null ) {
            // omBoundingBox.addAttribute( omCrs );
        }

        omElement.addChild( omBoundingBox );

    }

    /**
     * Adds the elements for the brief representation in ISO.
     * 
     * @return OMElement
     */
    private OMElement setISOBriefElements() {

        OMElement omElement;

        omElement = factory.createOMElement( "MD_Metadata", namespaceGMD );
        omElement.addChild( identifier );
        if ( hierarchyLevel != null ) {
            omElement.addChild( hierarchyLevel );
        }
        if ( identificationInfo != null ) {
            omElement.addChild( identificationInfo );
        }
        return omElement;

    }

    /**
     * Adds the elements for the summary representation in ISO.
     * 
     * @return OMElement
     */
    private OMElement setISOSummaryElements() {

        OMElement omElement;

        omElement = setISOBriefElements();

        if ( distributionInfo != null ) {
            omElement.addChild( distributionInfo );
        }
        if ( hierarchyLevelName != null ) {
            omElement.addChild( hierarchyLevelName );
        }
        if ( language != null ) {
            omElement.addChild( language );
        }
        if ( dataQualityInfo != null ) {
            omElement.addChild( dataQualityInfo );
        }
        if ( characterSet != null ) {
            omElement.addChild( characterSet );
        }
        if ( metadataStandardName != null ) {
            omElement.addChild( metadataStandardName );
        }
        if ( metadataStandardVersion != null ) {
            omElement.addChild( metadataStandardVersion );
        }
        if ( parentIdentifier != null ) {
            omElement.addChild( parentIdentifier );
        }
        if ( referenceSystemInfo != null ) {
            omElement.addChild( referenceSystemInfo );
        }

        return omElement;

    }

}
