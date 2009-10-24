package org.apache.maven.report.projectinfo.dependencies.renderer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.util.HtmlTools;
import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.report.projectinfo.ProjectInfoReportUtils;
import org.apache.maven.report.projectinfo.dependencies.Dependencies;
import org.apache.maven.report.projectinfo.dependencies.DependenciesReportConfiguration;
import org.apache.maven.report.projectinfo.dependencies.RepositoryUtils;
import org.apache.maven.reporting.AbstractMavenReportRenderer;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.jar.JarData;
import org.codehaus.plexus.i18n.I18N;
import org.codehaus.plexus.util.StringUtils;

/**
 * Renderer the dependencies report.
 *
 * @version $Id$
 * @since 2.1
 */
public class DependenciesRenderer
    extends AbstractMavenReportRenderer
{
    /** URL for the 'icon_info_sml.gif' image */
    private static final String IMG_INFO_URL = "./images/icon_info_sml.gif";

    /** URL for the 'close.gif' image */
    private static final String IMG_CLOSE_URL = "./images/close.gif";

    /** Random used to generate a UID */
    private static final SecureRandom RANDOM;

    /** Used to format decimal values in the "Dependency File Details" table */
    protected static final DecimalFormat DEFAULT_DECIMAL_FORMAT = new DecimalFormat( "#,##0" );

    private static final Set JAR_SUBTYPE = new HashSet();

    /**
     * An HTML script tag with the Javascript used by the dependencies report.
     */
    private static final String JAVASCRIPT;

    private final DependencyNode dependencyTreeNode;

    private final Dependencies dependencies;

    private final DependenciesReportConfiguration configuration;

    private final Locale locale;

    private final I18N i18n;

    private final Log log;

    private final Settings settings;

    private final RepositoryUtils repoUtils;

    /** Used to format file length values */
    private final DecimalFormat fileLengthDecimalFormat;

    /**
     * @since 2.1.1
     */
    private int section;

    /**
     * Will be filled with license name / set of projects.
     */
    private Map licenseMap = new HashMap()
    {
        /** {@inheritDoc} */
        public Object put( Object key, Object value )
        {
            // handle multiple values as a set to avoid duplicates
            SortedSet valueList = (SortedSet) get( key );
            if ( valueList == null )
            {
                valueList = new TreeSet();
            }
            valueList.add( value );
            return super.put( key, valueList );
        }
    };

    private final ArtifactFactory artifactFactory;

    private final MavenProjectBuilder mavenProjectBuilder;

    private final List remoteRepositories;

    private final ArtifactRepository localRepository;

    static
    {
        JAR_SUBTYPE.add( "jar" );
        JAR_SUBTYPE.add( "war" );
        JAR_SUBTYPE.add( "ear" );
        JAR_SUBTYPE.add( "sar" );
        JAR_SUBTYPE.add( "rar" );
        JAR_SUBTYPE.add( "par" );
        JAR_SUBTYPE.add( "ejb" );

        try
        {
            RANDOM = SecureRandom.getInstance( "SHA1PRNG" );
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new RuntimeException( e );
        }

        StringBuffer sb = new StringBuffer();
        sb.append( "<script language=\"javascript\" type=\"text/javascript\">" ).append( "\n" );
        sb.append( "      function toggleDependencyDetail( divId, imgId )" ).append( "\n" );
        sb.append( "      {" ).append( "\n" );
        sb.append( "        var div = document.getElementById( divId );" ).append( "\n" );
        sb.append( "        var img = document.getElementById( imgId );" ).append( "\n" );
        sb.append( "        if( div.style.display == '' )" ).append( "\n" );
        sb.append( "        {" ).append( "\n" );
        sb.append( "          div.style.display = 'none';" ).append( "\n" );
        sb.append( "          img.src='" + IMG_INFO_URL + "';" ).append( "\n" );
        sb.append( "        }" ).append( "\n" );
        sb.append( "        else" ).append( "\n" );
        sb.append( "        {" ).append( "\n" );
        sb.append( "          div.style.display = '';" ).append( "\n" );
        sb.append( "          img.src='" + IMG_CLOSE_URL + "';" ).append( "\n" );
        sb.append( "        }" ).append( "\n" );
        sb.append( "      }" ).append( "\n" );
        sb.append( "</script>" ).append( "\n" );
        JAVASCRIPT = sb.toString();
    }

    /**
     * Default constructor.
     *
     * @param sink
     * @param locale
     * @param i18n
     * @param log
     * @param settings
     * @param dependencies
     * @param dependencyTreeNode
     * @param config
     * @param repoUtils
     * @param artifactFactory
     * @param mavenProjectBuilder
     * @param remoteRepositories
     * @param localRepository
     */
    public DependenciesRenderer( Sink sink, Locale locale, I18N i18n, Log log, Settings settings,
                                 Dependencies dependencies, DependencyNode dependencyTreeNode,
                                 DependenciesReportConfiguration config, RepositoryUtils repoUtils,
                                 ArtifactFactory artifactFactory, MavenProjectBuilder mavenProjectBuilder,
                                 List remoteRepositories, ArtifactRepository localRepository )
    {
        super( sink );

        this.locale = locale;
        this.i18n = i18n;
        this.log = log;
        this.settings = settings;
        this.dependencies = dependencies;
        this.dependencyTreeNode = dependencyTreeNode;
        this.repoUtils = repoUtils;
        this.configuration = config;
        this.artifactFactory = artifactFactory;
        this.mavenProjectBuilder = mavenProjectBuilder;
        this.remoteRepositories = remoteRepositories;
        this.localRepository = localRepository;

        // Using the right set of symbols depending of the locale
        DEFAULT_DECIMAL_FORMAT.setDecimalFormatSymbols( new DecimalFormatSymbols( locale ) );

        this.fileLengthDecimalFormat = new FileDecimalFormat( i18n, locale );
        this.fileLengthDecimalFormat.setDecimalFormatSymbols( new DecimalFormatSymbols( locale ) );
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    /** {@inheritDoc} */
    public String getTitle()
    {
        return getReportString( "report.dependencies.title" );
    }

    /** {@inheritDoc} */
    public void renderBody()
    {
        // Dependencies report

        if ( !dependencies.hasDependencies() )
        {
            startSection( getTitle() );

            // TODO: should the report just be excluded?
            paragraph( getReportString( "report.dependencies.nolist" ) );

            endSection();

            return;
        }

        // === Section: Project Dependencies.
        renderSectionProjectDependencies();

        // === Section: Project Transitive Dependencies.
        renderSectionProjectTransitiveDependencies();

        // === Section: Project Dependency Graph.
        renderSectionProjectDependencyGraph();

        // === Section: Licenses
        renderSectionDependencyLicenseListing();

        if ( configuration.getDependencyDetailsEnabled() )
        {
            // === Section: Dependency File Details.
            renderSectionDependencyFileDetails();
        }

        if ( configuration.getDependencyLocationsEnabled() )
        {
            // === Section: Dependency Repository Locations.
            renderSectionDependencyRepositoryLocations();
        }
    }

    // ----------------------------------------------------------------------
    // Protected methods
    // ----------------------------------------------------------------------

    /** {@inheritDoc} */
    // workaround for MPIR-140
    // TODO Remove me when maven-reporting-impl:2.1-SNAPSHOT is out
    protected void startSection( String name )
    {
        startSection( name, name );
    }

    /**
     * Start section with a name and a specific anchor.
     *
     * @param anchor not null
     * @param name not null
     */
    protected void startSection( String anchor, String name )
    {
        section = section + 1;

        super.sink.anchor( HtmlTools.encodeId( anchor ) );
        super.sink.anchor_();

        switch ( section )
        {
            case 1:
                sink.section1();
                sink.sectionTitle1();
                break;
            case 2:
                sink.section2();
                sink.sectionTitle2();
                break;
            case 3:
                sink.section3();
                sink.sectionTitle3();
                break;
            case 4:
                sink.section4();
                sink.sectionTitle4();
                break;
            case 5:
                sink.section5();
                sink.sectionTitle5();
                break;

            default:
                // TODO: warning - just don't start a section
                break;
        }

        text( name );

        switch ( section )
        {
            case 1:
                sink.sectionTitle1_();
                break;
            case 2:
                sink.sectionTitle2_();
                break;
            case 3:
                sink.sectionTitle3_();
                break;
            case 4:
                sink.sectionTitle4_();
                break;
            case 5:
                sink.sectionTitle5_();
                break;

            default:
                // TODO: warning - just don't start a section
                break;
        }
    }

    /** {@inheritDoc} */
    // workaround for MPIR-140
    // TODO Remove me when maven-reporting-impl:2.1-SNAPSHOT is out
    protected void endSection()
    {
        switch ( section )
        {
            case 1:
                sink.section1_();
                break;
            case 2:
                sink.section2_();
                break;
            case 3:
                sink.section3_();
                break;
            case 4:
                sink.section4_();
                break;
            case 5:
                sink.section5_();
                break;

            default:
                // TODO: warning - just don't start a section
                break;
        }

        section = section - 1;

        if ( section < 0 )
        {
            throw new IllegalStateException( "Too many closing sections" );
        }
    }

    // ----------------------------------------------------------------------
    // Private methods
    // ----------------------------------------------------------------------

    /**
     * @param withClassifier <code>true</code> to include the classifier column, <code>false</code> otherwise.
     * @param withOptional <code>true</code> to include the optional column, <code>false</code> otherwise.
     * @return the dependency table header with/without classifier/optional column
     * @see #renderArtifactRow(Artifact, boolean, boolean)
     */
    private String[] getDependencyTableHeader( boolean withClassifier, boolean withOptional )
    {
        String groupId = getReportString( "report.dependencies.column.groupId" );
        String artifactId = getReportString( "report.dependencies.column.artifactId" );
        String version = getReportString( "report.dependencies.column.version" );
        String classifier = getReportString( "report.dependencies.column.classifier" );
        String type = getReportString( "report.dependencies.column.type" );
        String optional = getReportString( "report.dependencies.column.optional" );

        if ( withClassifier )
        {
            if ( withOptional )
            {
                return new String[] { groupId, artifactId, version, classifier, type, optional };
            }

            return new String[] { groupId, artifactId, version, classifier, type };
        }

        if ( withOptional )
        {
            return new String[] { groupId, artifactId, version, type, optional };
        }

        return new String[] { groupId, artifactId, version, type };
    }

    private void renderSectionProjectDependencies()
    {
        startSection( getTitle() );

        // collect dependencies by scope
        Map dependenciesByScope = dependencies.getDependenciesByScope( false );

        renderDependenciesForAllScopes( dependenciesByScope, false );

        endSection();
    }

    /**
     * @param dependenciesByScope map with supported scopes as key and a list of <code>Artifact</code> as values.
     * @param isTransitive <code>true</code> if it is transitive dependencies rendering.
     * @see Artifact#SCOPE_COMPILE
     * @see Artifact#SCOPE_PROVIDED
     * @see Artifact#SCOPE_RUNTIME
     * @see Artifact#SCOPE_SYSTEM
     * @see Artifact#SCOPE_TEST
     */
    private void renderDependenciesForAllScopes( Map dependenciesByScope, boolean isTransitive )
    {
        renderDependenciesForScope( Artifact.SCOPE_COMPILE,
                                    (List) dependenciesByScope.get( Artifact.SCOPE_COMPILE ), isTransitive );
        renderDependenciesForScope( Artifact.SCOPE_RUNTIME,
                                    (List) dependenciesByScope.get( Artifact.SCOPE_RUNTIME ), isTransitive );
        renderDependenciesForScope( Artifact.SCOPE_TEST, (List) dependenciesByScope.get( Artifact.SCOPE_TEST ),
                                    isTransitive );
        renderDependenciesForScope( Artifact.SCOPE_PROVIDED,
                                    (List) dependenciesByScope.get( Artifact.SCOPE_PROVIDED ), isTransitive );
        renderDependenciesForScope( Artifact.SCOPE_SYSTEM,
                                    (List) dependenciesByScope.get( Artifact.SCOPE_SYSTEM ), isTransitive );
    }

    private void renderSectionProjectTransitiveDependencies()
    {
        Map dependenciesByScope = dependencies.getDependenciesByScope( true );

        startSection( getReportString( "report.transitivedependencies.title" ) );

        if ( dependenciesByScope.values().isEmpty() )
        {
            paragraph( getReportString( "report.transitivedependencies.nolist" ) );
        }
        else
        {
            paragraph( getReportString( "report.transitivedependencies.intro" ) );

            renderDependenciesForAllScopes( dependenciesByScope, true );
        }

        endSection();
    }

    private void renderSectionProjectDependencyGraph()
    {
        startSection( getReportString( "report.dependencies.graph.title" ) );

        // === SubSection: Dependency Tree
        renderSectionDependencyTree();

        endSection();
    }

    private void renderSectionDependencyTree()
    {
        sink.rawText( JAVASCRIPT );

        // for Dependencies Graph Tree
        startSection( getReportString( "report.dependencies.graph.tree.title" ) );

        sink.list();
        printDependencyListing( dependencyTreeNode );
        sink.list_();

        endSection();
    }

    private void renderSectionDependencyFileDetails()
    {
        startSection( getReportString( "report.dependencies.file.details.title" ) );

        List alldeps = dependencies.getAllDependencies();
        Collections.sort( alldeps, getArtifactComparator() );

        // i18n
        String filename = getReportString( "report.dependencies.file.details.column.file" );
        String size = getReportString( "report.dependencies.file.details.column.size" );
        String entries = getReportString( "report.dependencies.file.details.column.entries" );
        String classes = getReportString( "report.dependencies.file.details.column.classes" );
        String packages = getReportString( "report.dependencies.file.details.column.packages" );
        String jdkrev = getReportString( "report.dependencies.file.details.column.jdkrev" );
        String debug = getReportString( "report.dependencies.file.details.column.debug" );
        String sealed = getReportString( "report.dependencies.file.details.column.sealed" );

        int[] justification =
            new int[] { Sink.JUSTIFY_LEFT, Sink.JUSTIFY_RIGHT, Sink.JUSTIFY_RIGHT, Sink.JUSTIFY_RIGHT,
                Sink.JUSTIFY_RIGHT, Sink.JUSTIFY_CENTER, Sink.JUSTIFY_CENTER, Sink.JUSTIFY_CENTER };

        startTable( justification, true );

        TotalCell totaldeps = new TotalCell( DEFAULT_DECIMAL_FORMAT );
        TotalCell totaldepsize = new TotalCell( fileLengthDecimalFormat );
        TotalCell totalentries = new TotalCell( DEFAULT_DECIMAL_FORMAT );
        TotalCell totalclasses = new TotalCell( DEFAULT_DECIMAL_FORMAT );
        TotalCell totalpackages = new TotalCell( DEFAULT_DECIMAL_FORMAT );
        double highestjdk = 0.0;
        TotalCell totaldebug = new TotalCell( DEFAULT_DECIMAL_FORMAT );
        TotalCell totalsealed = new TotalCell( DEFAULT_DECIMAL_FORMAT );

        boolean hasSealed = hasSealed( alldeps );

        // Table header
        String[] tableHeader;
        if ( hasSealed )
        {
            tableHeader = new String[] { filename, size, entries, classes, packages, jdkrev, debug, sealed };
        }
        else
        {
            tableHeader = new String[] { filename, size, entries, classes, packages, jdkrev, debug };
        }
        tableHeader( tableHeader );

        // Table rows
        for ( Iterator it = alldeps.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();

            if ( artifact.getFile() == null )
            {
                log.error( "Artifact: " + artifact.getId() + " has no file." );
                continue;
            }

            File artifactFile = artifact.getFile();

            totaldeps.incrementTotal( artifact.getScope() );
            totaldepsize.addTotal( artifactFile.length(), artifact.getScope() );

            if ( JAR_SUBTYPE.contains( artifact.getType().toLowerCase() ) )
            {
                try
                {
                    JarData jarDetails = dependencies.getJarDependencyDetails( artifact );

                    String debugstr = "release";
                    if ( jarDetails.isDebugPresent() )
                    {
                        debugstr = "debug";
                        totaldebug.incrementTotal( artifact.getScope() );
                    }

                    totalentries.addTotal( jarDetails.getNumEntries(), artifact.getScope() );
                    totalclasses.addTotal( jarDetails.getNumClasses(), artifact.getScope() );
                    totalpackages.addTotal( jarDetails.getNumPackages(), artifact.getScope() );

                    try
                    {
                        if ( jarDetails.getJdkRevision() != null )
                        {
                            highestjdk = Math.max( highestjdk, Double.parseDouble( jarDetails.getJdkRevision() ) );
                        }
                    }
                    catch ( NumberFormatException e )
                    {
                        // ignore
                    }

                    String sealedstr = "";
                    if ( jarDetails.isSealed() )
                    {
                        sealedstr = "sealed";
                        totalsealed.incrementTotal( artifact.getScope() );
                    }

                    tableRow( hasSealed, new String[] { artifactFile.getName(),
                        fileLengthDecimalFormat.format( artifactFile.length() ),
                        DEFAULT_DECIMAL_FORMAT.format( jarDetails.getNumEntries() ),
                        DEFAULT_DECIMAL_FORMAT.format( jarDetails.getNumClasses() ),
                        DEFAULT_DECIMAL_FORMAT.format( jarDetails.getNumPackages() ), jarDetails.getJdkRevision(),
                        debugstr, sealedstr } );
                }
                catch ( IOException e )
                {
                    createExceptionInfoTableRow( artifact, artifactFile, e, hasSealed );
                }
            }
            else
            {
                tableRow( hasSealed, new String[] { artifactFile.getName(),
                    fileLengthDecimalFormat.format( artifactFile.length() ), "", "", "", "", "", "" } );
            }
        }

        // Total raws
        tableHeader[0] = getReportString( "report.dependencies.file.details.total" );
        tableHeader( tableHeader );

        justification[0] = Sink.JUSTIFY_RIGHT;
        justification[6] = Sink.JUSTIFY_RIGHT;

        for ( int i = -1; i < TotalCell.SCOPES_COUNT; i++ )
        {
            if ( totaldeps.getTotal( i ) > 0 )
            {
                tableRow( hasSealed, new String[] { totaldeps.getTotalString( i ), totaldepsize.getTotalString( i ),
                    totalentries.getTotalString( i ), totalclasses.getTotalString( i ),
                    totalpackages.getTotalString( i ), ( i < 0 ) ? String.valueOf( highestjdk ) : "",
                    totaldebug.getTotalString( i ), totalsealed.getTotalString( i ) } );
            }
        }

        endTable();
        endSection();
    }

    private void tableRow( boolean fullRow, String[] content )
    {
        sink.tableRow();

        int count = fullRow ? content.length : ( content.length - 1 );

        for ( int i = 0; i < count; i++ )
        {
            tableCell( content[i] );
        }

        sink.tableRow_();
    }

    private void createExceptionInfoTableRow( Artifact artifact, File artifactFile, Exception e, boolean hasSealed )
    {
        tableRow( hasSealed, new String[] { artifact.getId(), artifactFile.getAbsolutePath(), e.getMessage(), "", "",
            "", "", "" } );
    }

    private void populateRepositoryMap( Map repos, List rowRepos )
    {
        Iterator it = rowRepos.iterator();
        while ( it.hasNext() )
        {
            ArtifactRepository repo = (ArtifactRepository) it.next();

            repos.put( repo.getId(), repo );
        }
    }

    private void blacklistRepositoryMap( Map repos, List repoUrlBlackListed )
    {
        for ( Iterator it = repos.keySet().iterator(); it.hasNext(); )
        {
            String key = (String) it.next();
            ArtifactRepository repo = (ArtifactRepository) repos.get( key );

            // ping repo
            if ( !repo.isBlacklisted() )
            {
                if ( !repoUrlBlackListed.contains( repo.getUrl() ) )
                {
                    try
                    {
                        URL repoUrl = new URL( repo.getUrl() );
                        if ( ProjectInfoReportUtils.getInputStream( repoUrl, settings ) == null )
                        {
                            log.warn( "The repository url '" + repoUrl + "' has no stream - Repository '"
                                + repo.getId() + "' will be blacklisted." );
                            repo.setBlacklisted( true );
                            repoUrlBlackListed.add( repo.getUrl() );
                        }
                    }
                    catch ( IOException e )
                    {
                        log.warn( "The repository url '" + repo.getUrl() + "' is invalid - Repository '" + repo.getId()
                            + "' will be blacklisted." );
                        repo.setBlacklisted( true );
                        repoUrlBlackListed.add( repo.getUrl() );
                    }
                }
                else
                {
                    repo.setBlacklisted( true );
                }
            }
            else
            {
                repoUrlBlackListed.add( repo.getUrl() );
            }
        }
    }

    private void renderSectionDependencyRepositoryLocations()
    {
        startSection( getReportString( "report.dependencies.repo.locations.title" ) );

        // Collect Alphabetical Dependencies
        List alldeps = dependencies.getAllDependencies();
        Collections.sort( alldeps, getArtifactComparator() );

        // Collect Repositories
        Map repoMap = new HashMap();

        populateRepositoryMap( repoMap, repoUtils.getRemoteArtifactRepositories() );
        for ( Iterator it = alldeps.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();
            try
            {
                MavenProject artifactProject = repoUtils.getMavenProjectFromRepository( artifact );
                populateRepositoryMap( repoMap, artifactProject.getRemoteArtifactRepositories() );
            }
            catch ( ProjectBuildingException e )
            {
                log.warn( "Unable to create Maven project from repository.", e );
            }
        }

        List repoUrlBlackListed = new ArrayList();
        blacklistRepositoryMap( repoMap, repoUrlBlackListed );

        // Render Repository List

        printRepositories( repoMap, repoUrlBlackListed );

        // Render Artifacts locations

        printArtifactsLocations( repoMap, alldeps );

        endSection();
    }

    private void renderSectionDependencyLicenseListing()
    {
        startSection( getReportString( "report.dependencies.graph.tables.licenses" ) );
        printGroupedLicenses();
        endSection();
    }

    private void renderDependenciesForScope( String scope, List artifacts, boolean isTransitive )
    {
        if ( artifacts != null )
        {
            boolean withClassifier = hasClassifier( artifacts );
            boolean withOptional = hasOptional( artifacts );
            String[] tableHeader = getDependencyTableHeader( withClassifier, withOptional );

            // can't use straight artifact comparison because we want optional last
            Collections.sort( artifacts, getArtifactComparator() );

            String anchorByScope =
                ( isTransitive ? getReportString( "report.transitivedependencies.title" ) + "_" + scope
                                : getReportString( "report.dependencies.title" ) + "_" + scope );
            startSection( anchorByScope, scope );

            paragraph( getReportString( "report.dependencies.intro." + scope ) );

            startTable();
            tableHeader( tableHeader );
            for ( Iterator iterator = artifacts.iterator(); iterator.hasNext(); )
            {
                Artifact artifact = (Artifact) iterator.next();

                renderArtifactRow( artifact, withClassifier, withOptional );
            }
            endTable();

            endSection();
        }
    }

    private Comparator getArtifactComparator()
    {
        return new Comparator()
        {
            public int compare( Object o1, Object o2 )
            {
                Artifact a1 = (Artifact) o1;
                Artifact a2 = (Artifact) o2;

                // put optional last
                if ( a1.isOptional() && !a2.isOptional() )
                {
                    return +1;
                }
                else if ( !a1.isOptional() && a2.isOptional() )
                {
                    return -1;
                }
                else
                {
                    return a1.compareTo( a2 );
                }
            }
        };
    }

    /**
     * @param artifact not null
     * @param withClassifier <code>true</code> to include the classifier column, <code>false</code> otherwise.
     * @param withOptional <code>true</code> to include the optional column, <code>false</code> otherwise.
     * @see #getDependencyTableHeader(boolean, boolean)
     */
    private void renderArtifactRow( Artifact artifact, boolean withClassifier, boolean withOptional )
    {
        String isOptional =
            artifact.isOptional() ? getReportString( "report.dependencies.column.isOptional" )
                            : getReportString( "report.dependencies.column.isNotOptional" );

        String url =
            ProjectInfoReportUtils.getArtifactUrl( artifactFactory, artifact, mavenProjectBuilder, remoteRepositories,
                                                   localRepository );
        String artifactIdCell = ProjectInfoReportUtils.getArtifactIdCell( artifact.getArtifactId(), url );

        String content[];
        if ( withClassifier )
        {
            content =
                new String[] { artifact.getGroupId(), artifactIdCell, artifact.getVersion(), artifact.getClassifier(),
                    artifact.getType(), isOptional };
        }
        else
        {
            content =
                new String[] { artifact.getGroupId(), artifactIdCell, artifact.getVersion(), artifact.getType(),
                    isOptional };
        }

        tableRow( withOptional, content );
    }

    private void printDependencyListing( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();
        String id = artifact.getId();
        String dependencyDetailId = getUUID();
        String imgId = getUUID();

        sink.listItem();

        sink.text( id + ( StringUtils.isNotEmpty( artifact.getScope() ) ? " (" + artifact.getScope() + ") " : " " ) );
        sink.rawText( "<img id=\"" + imgId + "\" src=\"" + IMG_INFO_URL
            + "\" alt=\"Information\" onclick=\"toggleDependencyDetail( '" + dependencyDetailId + "', '" + imgId
            + "' );\" style=\"cursor: pointer;vertical-align:text-bottom;\"></img>" );

        printDescriptionsAndURLs( node, dependencyDetailId );

        if ( !node.getChildren().isEmpty() )
        {
            boolean toBeIncluded = false;
            List subList = new ArrayList();
            for ( Iterator deps = node.getChildren().iterator(); deps.hasNext(); )
            {
                DependencyNode dep = (DependencyNode) deps.next();

                if ( !dependencies.getAllDependencies().contains( dep.getArtifact() ) )
                {
                    continue;
                }

                subList.add( dep );
                toBeIncluded = true;
            }

            if ( toBeIncluded )
            {
                sink.list();
                for ( Iterator deps = subList.iterator(); deps.hasNext(); )
                {
                    DependencyNode dep = (DependencyNode) deps.next();

                    printDependencyListing( dep );
                }
                sink.list_();
            }
        }

        sink.listItem_();
    }

    private void printDescriptionsAndURLs( DependencyNode node, String uid )
    {
        Artifact artifact = node.getArtifact();
        String id = artifact.getId();
        String unknownLicenseMessage = getReportString( "report.dependencies.graph.tables.unknown" );

        sink.rawText( "<div id=\"" + uid + "\" style=\"display:none\">" );

        sink.table();

        if ( !Artifact.SCOPE_SYSTEM.equals( artifact.getScope() ) )
        {
            try
            {
                MavenProject artifactProject = repoUtils.getMavenProjectFromRepository( artifact );
                String artifactDescription = artifactProject.getDescription();
                String artifactUrl = artifactProject.getUrl();
                String artifactName = artifactProject.getName();
                List licenses = artifactProject.getLicenses();

                sink.tableRow();
                sink.tableHeaderCell();
                sink.text( artifactName );
                sink.tableHeaderCell_();
                sink.tableRow_();

                sink.tableRow();
                sink.tableCell();

                sink.paragraph();
                sink.bold();
                sink.text( getReportString( "report.dependencies.column.description" ) + ": " );
                sink.bold_();
                if ( StringUtils.isNotEmpty( artifactDescription ) )
                {
                    sink.text( artifactDescription );
                }
                else
                {
                    sink.text( getReportString( "report.index.nodescription" ) );
                }
                sink.paragraph_();

                if ( StringUtils.isNotEmpty( artifactUrl ) )
                {
                    sink.paragraph();
                    sink.bold();
                    sink.text( getReportString( "report.dependencies.column.url" ) + ": " );
                    sink.bold_();
                    if ( ProjectInfoReportUtils.isArtifactUrlValid( artifactUrl ) )
                    {
                        sink.link( artifactUrl );
                        sink.text( artifactUrl );
                        sink.link_();
                    }
                    else
                    {
                        sink.text( artifactUrl );
                    }
                    sink.paragraph_();
                }

                sink.paragraph();
                sink.bold();
                sink.text( getReportString( "report.license.title" ) + ": " );
                sink.bold_();
                if ( !licenses.isEmpty() )
                {
                    for ( Iterator iter = licenses.iterator(); iter.hasNext(); )
                    {
                        License element = (License) iter.next();
                        String licenseName = element.getName();
                        String licenseUrl = element.getUrl();

                        if ( licenseUrl != null )
                        {
                            sink.link( licenseUrl );
                        }
                        sink.text( licenseName );

                        if ( licenseUrl != null )
                        {
                            sink.link_();
                        }

                        licenseMap.put( licenseName, artifactName );
                    }
                }
                else
                {
                    sink.text( getReportString( "report.license.nolicense" ) );

                    licenseMap.put( unknownLicenseMessage, artifactName );
                }
                sink.paragraph_();
            }
            catch ( ProjectBuildingException e )
            {
                log.error( "ProjectBuildingException error : ", e );
            }
        }
        else
        {
            sink.tableRow();
            sink.tableHeaderCell();
            sink.text( id );
            sink.tableHeaderCell_();
            sink.tableRow_();

            sink.tableRow();
            sink.tableCell();

            sink.paragraph();
            sink.bold();
            sink.text( getReportString( "report.dependencies.column.description" ) + ": " );
            sink.bold_();
            sink.text( getReportString( "report.index.nodescription" ) );
            sink.paragraph_();

            if ( artifact.getFile() != null )
            {
                sink.paragraph();
                sink.bold();
                sink.text( getReportString( "report.dependencies.column.url" ) + ": " );
                sink.bold_();
                sink.text( artifact.getFile().getAbsolutePath() );
                sink.paragraph_();
            }
        }

        sink.tableCell_();
        sink.tableRow_();

        sink.table_();

        sink.rawText( "</div>" );
    }

    private void printGroupedLicenses()
    {
        for ( Iterator iter = licenseMap.keySet().iterator(); iter.hasNext(); )
        {
            String licenseName = (String) iter.next();
            sink.paragraph();
            sink.bold();
            if ( StringUtils.isEmpty( licenseName ) )
            {
                sink.text( i18n.getString( "project-info-report", locale, "report.dependencies.unamed" ) );
            }
            else
            {
                sink.text( licenseName );
            }
            sink.text( ": " );
            sink.bold_();

            SortedSet projects = (SortedSet) licenseMap.get( licenseName );

            for ( Iterator iterator = projects.iterator(); iterator.hasNext(); )
            {
                String projectName = (String) iterator.next();
                sink.text( projectName );
                if ( iterator.hasNext() )
                {
                    sink.text( ", " );
                }
            }

            sink.paragraph_();
        }
    }

    private void printRepositories( Map repoMap, List repoUrlBlackListed )
    {
        // i18n
        String repoid = getReportString( "report.dependencies.repo.locations.column.repoid" );
        String url = getReportString( "report.dependencies.repo.locations.column.url" );
        String release = getReportString( "report.dependencies.repo.locations.column.release" );
        String snapshot = getReportString( "report.dependencies.repo.locations.column.snapshot" );
        String blacklisted = getReportString( "report.dependencies.repo.locations.column.blacklisted" );
        String releaseEnabled = getReportString( "report.dependencies.repo.locations.cell.release.enabled" );
        String releaseDisabled = getReportString( "report.dependencies.repo.locations.cell.release.disabled" );
        String snapshotEnabled = getReportString( "report.dependencies.repo.locations.cell.snapshot.enabled" );
        String snapshotDisabled = getReportString( "report.dependencies.repo.locations.cell.snapshot.disabled" );
        String blacklistedEnabled = getReportString( "report.dependencies.repo.locations.cell.blacklisted.enabled" );
        String blacklistedDisabled = getReportString( "report.dependencies.repo.locations.cell.blacklisted.disabled" );

        // Table header

        String[] tableHeader;
        int[] justificationRepo;
        if ( repoUrlBlackListed.isEmpty() )
        {
            tableHeader = new String[] { repoid, url, release, snapshot };
            justificationRepo =
                new int[] { Sink.JUSTIFY_LEFT, Sink.JUSTIFY_LEFT, Sink.JUSTIFY_CENTER, Sink.JUSTIFY_CENTER };
        }
        else
        {
            tableHeader = new String[] { repoid, url, release, snapshot, blacklisted };
            justificationRepo =
                new int[] { Sink.JUSTIFY_LEFT, Sink.JUSTIFY_LEFT, Sink.JUSTIFY_CENTER, Sink.JUSTIFY_CENTER,
                    Sink.JUSTIFY_CENTER };
        }

        startTable( justificationRepo, true );

        tableHeader( tableHeader );

        // Table rows

        for ( Iterator it = repoMap.keySet().iterator(); it.hasNext(); )
        {
            String key = (String) it.next();
            ArtifactRepository repo = (ArtifactRepository) repoMap.get( key );

            sink.tableRow();
            tableCell( repo.getId() );

            sink.tableCell();
            if ( repo.isBlacklisted() )
            {
                sink.text( repo.getUrl() );
            }
            else
            {
                sink.link( repo.getUrl() );
                sink.text( repo.getUrl() );
                sink.link_();
            }
            sink.tableCell_();

            ArtifactRepositoryPolicy releasePolicy = repo.getReleases();
            tableCell( releasePolicy.isEnabled() ? releaseEnabled : releaseDisabled );

            ArtifactRepositoryPolicy snapshotPolicy = repo.getSnapshots();
            tableCell( snapshotPolicy.isEnabled() ? snapshotEnabled : snapshotDisabled );

            if ( !repoUrlBlackListed.isEmpty() )
            {
                tableCell( repo.isBlacklisted() ? blacklistedEnabled : blacklistedDisabled );
            }
            sink.tableRow_();
        }

        endTable();
    }

    private void printArtifactsLocations( Map repoMap, List alldeps )
    {
        // i18n
        String artifact = getReportString( "report.dependencies.repo.locations.column.artifact" );

        sink.paragraph();
        sink.text( getReportString( "report.dependencies.repo.locations.artifact.breakdown" ) );
        sink.paragraph_();

        List repoIdList = new ArrayList();
        // removed blacklisted repo
        for ( Iterator it = repoMap.keySet().iterator(); it.hasNext(); )
        {
            String repokey = (String) it.next();
            ArtifactRepository repo = (ArtifactRepository) repoMap.get( repokey );
            if ( !repo.isBlacklisted() )
            {
                repoIdList.add( repokey );
            }
        }

        String[] tableHeader = new String[repoIdList.size() + 1];
        int[] justificationRepo = new int[repoIdList.size() + 1];

        tableHeader[0] = artifact;
        justificationRepo[0] = Sink.JUSTIFY_LEFT;

        int idnum = 1;
        for ( Iterator it = repoIdList.iterator(); it.hasNext(); )
        {
            String id = (String) it.next();
            tableHeader[idnum] = id;
            justificationRepo[idnum] = Sink.JUSTIFY_CENTER;
            idnum++;
        }

        Map totalByRepo = new HashMap();
        TotalCell totaldeps = new TotalCell( DEFAULT_DECIMAL_FORMAT );

        startTable( justificationRepo, true );

        tableHeader( tableHeader );

        for ( Iterator it = alldeps.iterator(); it.hasNext(); )
        {
            Artifact dependency = (Artifact) it.next();

            totaldeps.incrementTotal( dependency.getScope() );

            sink.tableRow();

            if ( !Artifact.SCOPE_SYSTEM.equals( dependency.getScope() ) )
            {
                tableCell( dependency.getId() );

                for ( Iterator itrepo = repoIdList.iterator(); itrepo.hasNext(); )
                {
                    String repokey = (String) itrepo.next();
                    ArtifactRepository repo = (ArtifactRepository) repoMap.get( repokey );

                    String depUrl = repoUtils.getDependencyUrlFromRepository( dependency, repo );

                    Integer old = (Integer) totalByRepo.get( repokey );
                    if ( old == null )
                    {
                        totalByRepo.put( repokey, new Integer( 0 ) );
                        old = new Integer( 0 );
                    }

                    boolean dependencyExists = false;
                    // check snapshots in snapshots repository only and releases in release repositories...
                    if ( ( dependency.isSnapshot() && repo.getSnapshots().isEnabled() )
                        || ( !dependency.isSnapshot() && repo.getReleases().isEnabled() ) )
                    {
                        dependencyExists = repoUtils.dependencyExistsInRepo( repo, dependency );
                    }

                    if ( dependencyExists )
                    {
                        sink.tableCell();
                        if ( StringUtils.isNotEmpty( depUrl ) )
                        {
                            sink.link( depUrl );
                        }
                        else
                        {
                            sink.text( depUrl );
                        }

                        sink.figure();
                        sink.figureCaption();
                        sink.text( "Found at " + repo.getUrl() );
                        sink.figureCaption_();
                        sink.figureGraphics( "images/icon_success_sml.gif" );
                        sink.figure_();

                        sink.link_();
                        sink.tableCell_();

                        totalByRepo.put( repokey, new Integer( old.intValue() + 1 ) );
                    }
                    else
                    {
                        tableCell( "-" );
                    }
                }
            }
            else
            {
                tableCell( dependency.getId() );

                for ( Iterator itrepo = repoIdList.iterator(); itrepo.hasNext(); )
                {
                    itrepo.next();

                    tableCell( "-" );
                }
            }

            sink.tableRow_();
        }

        // Total row

        // reused key
        tableHeader[0] = getReportString( "report.dependencies.file.details.total" );
        tableHeader( tableHeader );
        String[] totalRow = new String[repoIdList.size() + 1];
        totalRow[0] = totaldeps.toString();
        idnum = 1;
        for ( Iterator itrepo = repoIdList.iterator(); itrepo.hasNext(); )
        {
            String repokey = (String) itrepo.next();

            Integer dependencies = (Integer) totalByRepo.get( repokey );
            totalRow[idnum++] = dependencies != null ? dependencies.toString() : "0";
        }

        tableRow( totalRow );

        endTable();
    }

    private String getReportString( String key )
    {
        return i18n.getString( "project-info-report", locale, key );
    }

    /**
     * @param artifacts not null
     * @return <code>true</code> if one artifact in the list has a classifier, <code>false</code> otherwise.
     */
    private boolean hasClassifier( List artifacts )
    {
        for ( Iterator iterator = artifacts.iterator(); iterator.hasNext(); )
        {
            Artifact artifact = (Artifact) iterator.next();

            if ( StringUtils.isNotEmpty( artifact.getClassifier() ) )
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @param artifacts not null
     * @return <code>true</code> if one artifact in the list is optional, <code>false</code> otherwise.
     */
    private boolean hasOptional( List artifacts )
    {
        for ( Iterator iterator = artifacts.iterator(); iterator.hasNext(); )
        {
            Artifact artifact = (Artifact) iterator.next();

            if ( artifact.isOptional() )
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @param artifacts not null
     * @return <code>true</code> if one artifact in the list is sealed, <code>false</code> otherwise.
     */
    private boolean hasSealed( List artifacts )
    {
        for ( Iterator it = artifacts.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();

            // TODO site:run Why do we need to resolve this...
            if ( artifact.getFile() == null && !Artifact.SCOPE_SYSTEM.equals( artifact.getScope() ) )
            {
                try
                {
                    repoUtils.resolve( artifact );
                }
                catch ( ArtifactResolutionException e )
                {
                    log.error( "Artifact: " + artifact.getId() + " has no file.", e );
                    continue;
                }
                catch ( ArtifactNotFoundException e )
                {
                    if ( ( dependencies.getProject().getGroupId().equals( artifact.getGroupId() ) )
                        && ( dependencies.getProject().getArtifactId().equals( artifact.getArtifactId() ) )
                        && ( dependencies.getProject().getVersion().equals( artifact.getVersion() ) ) )
                    {
                        log.warn( "The artifact of this project has never been deployed." );
                    }
                    else
                    {
                        log.error( "Artifact: " + artifact.getId() + " has no file.", e );
                    }

                    continue;
                }
            }

            if ( JAR_SUBTYPE.contains( artifact.getType().toLowerCase() ) )
            {
                try
                {
                    JarData jarDetails = dependencies.getJarDependencyDetails( artifact );
                    if ( jarDetails.isSealed() )
                    {
                        return true;
                    }
                }
                catch ( IOException e )
                {
                    log.error( "IOException: " + e.getMessage(), e );
                }
            }
        }
        return false;
    }

    /**
     * @return a valid HTML ID respecting
     * <a href="http://www.w3.org/TR/xhtml1/#C_8">XHTML 1.0 section C.8. Fragment Identifiers</a>
     */
    private static String getUUID()
    {
        return "_" + Math.abs( RANDOM.nextInt() );
    }

    /**
     * Formats file length with the associated <a href="http://en.wikipedia.org/wiki/SI_prefix#Computing">SI</a>
     * unit (GB, MB, kB) and using the pattern <code>########.00</code> by default.
     *
     * @see <a href="http://en.wikipedia.org/wiki/SI_prefix#Computing>
     * http://en.wikipedia.org/wiki/SI_prefix#Computing</a>
     * @see <a href="http://en.wikipedia.org/wiki/Binary_prefix">
     * http://en.wikipedia.org/wiki/Binary_prefix</a>
     * @see <a href="http://en.wikipedia.org/wiki/Octet_(computing)">
     * http://en.wikipedia.org/wiki/Octet_(computing)</a>
     */
    static class FileDecimalFormat
        extends DecimalFormat
    {
        private static final long serialVersionUID = 4062503546523610081L;

        private final I18N i18n;

        private final Locale locale;

        /**
         * Default constructor
         *
         * @param i18n
         * @param locale
         */
        public FileDecimalFormat( I18N i18n, Locale locale )
        {
            super( "#,###.00" );

            this.i18n = i18n;
            this.locale = locale;
        }

        /** {@inheritDoc} */
        public StringBuffer format( long fs, StringBuffer result, FieldPosition fieldPosition )
        {
            if ( fs > 1024 * 1024 * 1024 )
            {
                result = super.format( (float) fs / ( 1024 * 1024 * 1024 ), result, fieldPosition );
                result.append( " " ).append( getString( i18n, "report.dependencies.file.details.column.size.gb" ) );
                return result;
            }

            if ( fs > 1024 * 1024 )
            {
                result = super.format( (float) fs / ( 1024 * 1024 ), result, fieldPosition );
                result.append( " " ).append( getString( i18n, "report.dependencies.file.details.column.size.mb" ) );
                return result;
            }

            result = super.format( (float) fs / ( 1024 ), result, fieldPosition );
            result.append( " " ).append( getString( i18n, "report.dependencies.file.details.column.size.kb" ) );
            return result;
        }

        private String getString( I18N i18n, String key )
        {
            return i18n.getString( "project-info-report", locale, key );
        }
    }

    /**
     * Combine total and total by scope in a cell.
     */
    static class TotalCell
    {
        static final int SCOPES_COUNT = 5;

        final DecimalFormat decimalFormat;

        long total = 0;

        long totalCompileScope = 0;

        long totalTestScope = 0;

        long totalRuntimeScope = 0;

        long totalProvidedScope = 0;

        long totalSystemScope = 0;

        TotalCell( DecimalFormat decimalFormat )
        {
            this.decimalFormat = decimalFormat;
        }

        void incrementTotal( String scope )
        {
            addTotal( 1, scope );
        }

        static String getScope( int index )
        {
            switch ( index )
            {
                case 0:
                    return Artifact.SCOPE_COMPILE;
                case 1:
                    return Artifact.SCOPE_TEST;
                case 2:
                    return Artifact.SCOPE_RUNTIME;
                case 3:
                    return Artifact.SCOPE_PROVIDED;
                case 4:
                    return Artifact.SCOPE_SYSTEM;
                default:
                    return null;
            }
        }

        long getTotal( int index )
        {
            switch ( index )
            {
                case 0:
                    return totalCompileScope;
                case 1:
                    return totalTestScope;
                case 2:
                    return totalRuntimeScope;
                case 3:
                    return totalProvidedScope;
                case 4:
                    return totalSystemScope;
                default:
                    return total;
            }
        }

        String getTotalString( int index )
        {
            long total = getTotal( index );

            if ( total <= 0 )
            {
                return "";
            }

            StringBuffer sb = new StringBuffer();
            if ( index >= 0 )
            {
                sb.append( getScope( index ) ).append( ": " );
            }
            sb.append( decimalFormat.format( getTotal( index ) ) );
            return sb.toString();
        }

        void addTotal( long add, String scope )
        {
            total += add;

            if ( Artifact.SCOPE_COMPILE.equals( scope ) )
            {
                totalCompileScope += add;
            }
            else if ( Artifact.SCOPE_TEST.equals( scope ) )
            {
                totalTestScope += add;
            }
            else if ( Artifact.SCOPE_RUNTIME.equals( scope ) )
            {
                totalRuntimeScope += add;
            }
            else if ( Artifact.SCOPE_PROVIDED.equals( scope ) )
            {
                totalProvidedScope += add;
            }
            else if ( Artifact.SCOPE_SYSTEM.equals( scope ) )
            {
                totalSystemScope += add;
            }
        }

        /** {@inheritDoc} */
        public String toString()
        {
            StringBuffer sb = new StringBuffer();
            sb.append( decimalFormat.format( total ) );
            sb.append( " (" );

            boolean needSeparator = false;
            for ( int i = 0; i < SCOPES_COUNT; i++ )
            {
                if ( getTotal( i ) > 0 )
                {
                    if ( needSeparator )
                    {
                        sb.append( ", " );
                    }
                    sb.append( getTotalString( i ) );
                    needSeparator = true;
                }
            }

            sb.append( ")" );

            return sb.toString();
        }
    }
}