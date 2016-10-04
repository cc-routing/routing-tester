/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.blahami2.routingsaratester;

import cz.blahami2.routingsaratester.logic.TestRunner;
import cz.blahami2.routingsaratester.model.GralPlot;
import cz.blahami2.routingsaratester.model.TestOptions;
import cz.blahami2.routingsaratester.model.TestResult;
import cz.blahami2.utils.table.data.CsvTableExporter;
import cz.blahami2.utils.table.data.TableExporter;
import cz.blahami2.utils.table.model.DoubleListTableBuilder;
import cz.blahami2.utils.table.model.Table;
import cz.blahami2.utils.table.model.TableBuilder;
import cz.certicon.routing.algorithm.sara.preprocessing.assembly.Assembler;
import cz.certicon.routing.algorithm.sara.preprocessing.assembly.GreedyAssembler;
import cz.certicon.routing.algorithm.sara.preprocessing.filtering.Filter;
import cz.certicon.routing.algorithm.sara.preprocessing.filtering.NaturalCutsFilter;
import cz.certicon.routing.data.GraphDAO;
import cz.certicon.routing.data.GraphDataUpdater;
import cz.certicon.routing.data.GraphDeleteMessenger;
import cz.certicon.routing.data.SqliteGraphDAO;
import cz.certicon.routing.data.SqliteGraphDataUpdater;
import cz.certicon.routing.data.processor.GraphComponentSearcher;
import cz.certicon.routing.model.basic.MaxIdContainer;
import cz.certicon.routing.model.graph.Graph;
import cz.certicon.routing.model.graph.SaraGraph;
import cz.certicon.routing.model.graph.preprocessing.ContractGraph;
import cz.certicon.routing.model.values.TimeUnits;
import cz.certicon.routing.utils.DisplayUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import javax.swing.JFrame;

/**
 *
 * @author Michael Blaha {@literal <michael.blaha@gmail.com>}
 */
public class Main {

    private static final TestOptions DEFAULT_OPTIONS = new TestOptions( 10000, 1, 0.1, 0.03, 0.6, 200 );

    /**
     * @param args the command line arguments
     * @throws java.io.IOException coz i can
     */
    public static void main( String[] args ) throws IOException {
//        new Main().run();
//        new Main().test();
//        new Main().reduce();
        new Main().testPlot();
    }

    private List<String> toList( TestOptions options, TestResult result ) {
        List<String> list = new ArrayList<>();
        list.add( Integer.toString( options.getCellSize() ) );
        list.add( Double.toString( options.getCellRatio() ) );
        list.add( Double.toString( options.getCoreRatio() ) );
        list.add( Double.toString( options.getLowIntervalProbability() ) );
        list.add( Double.toString( options.getLowIntervalLimit() ) );
        list.add( Integer.toString( options.getNumberOfAssemblyRuns() ) );
        list.add( Integer.toString( result.getNumberOfCells() ) );
        list.add( Integer.toString( result.getMinimalCellSize() ) );
        list.add( Integer.toString( result.getMaximalCellSize() ) );
        list.add( Integer.toString( result.getMedianCellSize() ) );
        list.add( Integer.toString( (int) result.getAverageCellSize() ) );
        list.add( Integer.toString( result.getNumberOfCutEdges() ) );
        list.add( Long.toString( result.getFilteringTime().getTime( TimeUnits.MILLISECONDS ) ) );
        list.add( Long.toString( result.getAssemblyTime().getTime( TimeUnits.MILLISECONDS ) ) );
        return list;
    }

    /*
    Cell size: 1000; 50000; *2
    Cell ratio: 0.1; 1; +0.1
    Core ratio: 0.1; 1; +0.1
    Low interval probability: 0.01; 0.5; *2
    Low interval <0,?>: 0.1; 0.9; +0.1
    Number of assembly runs: 1; 1000; +50
Default
    Cell size: 10000
    Cell ratio: 1
    Core ratio: 0.1
    Low interval probability: 0.03
    Low interval <0,?>: 0.6
    Number of assembly runs: 1000
     */
    public void run() throws IOException {
        TableBuilder<String> tableBuilder = new DoubleListTableBuilder<>();
        tableBuilder.setHeaders( Arrays.asList( "cell size", "cell ratio", "core ratio", "low interval prob", "low interval lim", "#assembly runs", "#cells", "min cell", "max cell", "median cell", "avg cell", "#cut edges", "filtering[ms]", "assembly[ms]" ) );
        GraphDAO graphDAO = new SqliteGraphDAO( loadProperties() );
        Graph graph = graphDAO.loadGraph();
        System.out.println( "Testing cell size..." );
        for ( int cellSize = 500; cellSize < 510; cellSize *= 2 ) {
            TestOptions options = DEFAULT_OPTIONS.withCellSize( cellSize );
            TestRunner runner = new TestRunner( graph, options );
            TestResult result = runner.runForResult();
            tableBuilder.addRow( toList( options, result ) );
        }
        Table<String> table = tableBuilder.build();
        TableExporter exporter = new CsvTableExporter( CsvTableExporter.Delimiter.SEMICOLON );
        exporter.export( new File( "testing_result.csv" ), table, str -> str );
    }

    public void test() throws IOException {
        GraphDAO graphDAO = new SqliteGraphDAO( loadProperties() );
        TestOptions input = DEFAULT_OPTIONS.withCellSize( 100 ).withNumberOfAssemblyRuns( 1000 );
        Graph graph = graphDAO.loadGraph();
        TestResult.TestResultBuilder builder = TestResult.builder();
        System.out.println( "Filtering..." );
        Filter filter = new NaturalCutsFilter( input.getCellRatio(), 1 / input.getCoreRatio(), input.getCellSize() );
        ContractGraph filteredGraph = filter.filter( graph );
        System.out.println( "Assembly..." );
        Assembler assembler = new GreedyAssembler( input.getLowIntervalProbability(), input.getLowIntervalLimit(), input.getCellSize() );
        MaxIdContainer cellId = new MaxIdContainer( 0 );
        SaraGraph saraGraph = assembler.assemble( graph, filteredGraph, cellId, 3 );
        for ( int i = 0; i < input.getNumberOfAssemblyRuns(); i++ ) {
            SaraGraph assemble = assembler.assemble( graph, filteredGraph, cellId, 3 );
            int newCount = (int) StreamSupport.stream( assemble.getEdges().spliterator(), true )
                    .filter( edge -> !edge.getSource().getParent().equals( edge.getTarget().getParent() ) )
                    .count();
            int oldCount = (int) StreamSupport.stream( saraGraph.getEdges().spliterator(), true )
                    .filter( edge -> !edge.getSource().getParent().equals( edge.getTarget().getParent() ) )
                    .count();
            if ( newCount < oldCount ) {
                saraGraph = assemble;
            }
        }
        System.out.println( "Saving..." );
        graphDAO.saveGraph( saraGraph );
//        System.out.println( "#cutedges = " + (int) StreamSupport.stream( saraGraph.getEdges().spliterator(), true )
//                .filter( edge -> !edge.getSource().getParent().equals( edge.getTarget().getParent() ) )
//                .count() );
//        saraGraph = graphDAO.loadSaraGraph();
//        System.out.println( "#cutedges = " + (int) StreamSupport.stream( saraGraph.getEdges().spliterator(), true )
//                .filter( edge -> !edge.getSource().getParent().equals( edge.getTarget().getParent() ) )
//                .count() );
        DisplayUtils.display( saraGraph );
    }

    public void reduce() throws IOException {
        Properties properties = loadProperties();
        GraphDAO graphDAO = new SqliteGraphDAO( properties );
        System.out.println( "Loading graph..." );
        Graph graph = graphDAO.loadGraph();
        System.out.println( "Graph loaded. Searching for isolated areas..." );
        GraphDataUpdater dataUpdater = new SqliteGraphDataUpdater( properties );
        GraphDeleteMessenger isolatedAreas = new GraphComponentSearcher().findAllButLargest( graph );
        System.out.println( "Isolated areas found. Deleting..." );
        dataUpdater.deleteIsolatedAreas( isolatedAreas );
        System.out.println( "Deleted." );

    }

    private Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        InputStream in = getClass().getClassLoader().getResourceAsStream( "spatialite.properties" );
        properties.load( in );
        in.close();
        return properties;
    }

    public void testPlot() {

        TableBuilder<String> builder = new DoubleListTableBuilder<>();
        builder.setHeader( 0, "X");
        builder.setHeader( 1, "JednaY");
        builder.setHeader( 2, "DvaY");
        builder.setCell( 0, 0, "A" );
        builder.setCell( 0, 1, "NulaJedna" );
        builder.setCell( 0, 2, "NulaDva" );
        builder.setCell( 1, 0, "AA" );
        builder.setCell( 1, 1, "JednaJedna" );
        builder.setCell( 1, 2, "JednaDva" );
        builder.setCell( 2, 0, "AAA" );
        builder.setCell( 2, 1, "DvaJedna" );
        builder.setCell( 2, 2, "DvaDva" );
        builder.setCell( 3, 0, "AAAA" );
        builder.setCell( 3, 1, "TriJedna" );
        builder.setCell( 3, 2, "TriDva" );
        builder.setCell( 4, 0, "AAAAA" );
        builder.setCell( 4, 1, "CtyriJedna" );
        builder.setCell( 4, 2, "CtyriDva" );
        Table<String> table = builder.build();
        System.out.println( "Table:" + table );
        System.out.println( "Table:" + table.toString( x -> Integer.toString( x.length() ) ) );
        Function<String, Double> mapper = x -> Double.valueOf( x.length() );
        JFrame frame = new JFrame( "Plot#1" );
//        frame.setSize( 800, 600 );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        GralPlot instance = new GralPlot( table, mapper );
        instance.display( frame );
        frame.setVisible( true );
//        frame = new JFrame( "Plot#2" );
//        frame.setSize( 800, 600 );
//        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
//        instance.setData( table, mapper, 0, 1 );
//        instance.display( frame );
//        frame.setVisible( true );
    }
}
