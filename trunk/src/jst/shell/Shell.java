package jst.shell;

import jst.TemplateContext;
import jst.FileTemplateLoader;
import jst.ScriptRuntime;

import java.io.IOException;
import java.io.File;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

import jst.TemplateLoader;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;

import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.AttributeSet;
import javax.swing.*;

public class Shell {

    JFrame frame;
    JTextField commandField;
    JTextPane commandOutput;
    JScrollPane scrollPane;
    List<String> commandHistory = new ArrayList<String>();
    int currentCommand = 0;

    TemplateContext templateContext;
    ScriptRuntime runtime;
    String[] filePaths;

    public Shell(String[] filePaths) throws IOException {
        templateContext = new TemplateContext();
        templateContext.addLoader( new FileTemplateLoader( new File(".") ) );

        runtime = templateContext.start();
        runtime.include("core/shell.js");
        runtime.addGlobalVariable("shell", this);

        this.filePaths = filePaths;
    }

    public Object eval( String input ) throws IOException {
        return runtime.evaluate( input );
    }

    public void start() {
        commandField = new JTextField();
        commandField.getInputMap( JComponent.WHEN_FOCUSED ).put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0, false ), "evaluate" );
        commandField.getInputMap( JComponent.WHEN_FOCUSED ).put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0, false ), "previousCommand" );
        commandField.getInputMap( JComponent.WHEN_FOCUSED ).put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0, false ), "nextCommand" );
        commandField.getActionMap().put("evaluate", new AbstractAction() {
            public void actionPerformed(ActionEvent event) {
                try {
                    String command = commandField.getText();
                    if( command.length() > 0 ) {
                        commandHistory.add( command );
                        currentCommand = commandHistory.size();
                        println(command);
                        scrollPane.getVerticalScrollBar().setValue( scrollPane.getVerticalScrollBar().getMaximum() );
                        Object value = eval( command );
                        String result = Context.toString(value);
                        println(result);
                    }
                } catch( EcmaError error ) {
                    printError( "On line: " + error.lineNumber() + ": " + error.getErrorMessage() );
                } catch( EvaluatorException ex ) {
                    printError("On line: " + ex.lineNumber() + ": " + ex.getMessage() + "(" + ex.getLineSource() + ")" );
                } catch( JavaScriptException e ) {
                    printError("On line: " + e.lineNumber() + ": " + e.getMessage() );
                } catch (IOException e) {
                    printError( e.toString() );
                } finally {
                    print( "> " );
                    commandField.setText("");
                }
            }
        });
        commandField.getActionMap().put("previousCommand", new AbstractAction() {
            public void actionPerformed(ActionEvent event) {
                if( currentCommand > 0 ) {
                    currentCommand--;
                    commandField.setText( commandHistory.get( currentCommand ) );
                    commandField.selectAll();
                }
            }
        });
        commandField.getActionMap().put("nextCommand", new AbstractAction() {
            public void actionPerformed(ActionEvent event) {
                if( currentCommand < commandHistory.size() - 1 ) {
                    currentCommand++;
                    commandField.setText( commandHistory.get( currentCommand ) );
                    commandField.selectAll();
                }
            }
        });
        commandOutput = new JTextPane();
        commandOutput.setEditable(false);
        scrollPane = new JScrollPane( commandOutput );

        frame = new JFrame("Jst4J Shell");
        frame.setLayout( new BorderLayout( 5, 5 ) );
        frame.add( scrollPane, BorderLayout.CENTER );
        frame.add( commandField, BorderLayout.SOUTH );
        frame.setSize( 800, 600 );
        frame.setVisible(true);

        println( "Jst4J Shell version 1.0" );

        for( String arg : filePaths ) {
            File path = new File( arg );
            if( path.exists() ) {
                println( String.format("Adding script location %s", arg) );
                templateContext.addLoader( new FileTemplateLoader( path ) );
            } else {
                printError( String.format("WARNING: Script location %s does not exist.%n", path.getAbsolutePath() ) );
            }
        }

        print( "> " );
        commandField.requestFocus();
    }

    public List<String> getPaths() {
        List<TemplateLoader> loaders = templateContext.getLoaders();
        List<String> paths = new ArrayList<String>( loaders.size() );
        for( TemplateLoader loader : loaders ) {
            paths.add( loader.getRootUrl() );
        }
        return paths;
    }

    public void println(String result) {
        print( result );
        print( "\n" );
    }

    public void println() {
        print("\n");
    }

    public void println(String result, AttributeSet attributes) {
        print( result, attributes );
        print( "\n", attributes );
    }

    public void print( String text ) {
        print( text, null );
    }

    public void print( String text, AttributeSet attributes ) {
        try {
            commandOutput.getDocument().insertString( commandOutput.getDocument().getLength(), text, attributes );
        } catch( BadLocationException ex ) {
            JOptionPane.showMessageDialog( frame, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE  );
        }
    }

    public void printf( String format, Object... args ) {
        print( String.format(format,args) );
    }

    public void printError(String result) {
        MutableAttributeSet error = commandOutput.getInputAttributes();
        StyleConstants.setForeground(error, Color.RED);
        print( result, error );
        print( "\n", error );
    }

    public static void main(final String[] args) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                try {
                    Shell shell = new Shell( args );

                    shell.start();
                } catch( IOException ioe ) {
                    JOptionPane.showMessageDialog( null, ioe.toString() );
                }
            }
        });
    }
}
