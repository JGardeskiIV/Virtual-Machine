/************************************************************************************
 *
 * Class name:    CodeWriter
 * Description:   Translates VM commands into Hack assembly code.
 *
 * History:       Mar. 13, J, author, stubs & documentation based off of Ch. 7 UML
 *                Mar. 13-14, J, defined constructors, basic public & private methods
 *
 * Methods:       Public:   CodeWriter(String), CodeWriter(String, String),
 *                          setFileName(String), close(),
 *                          writeArithmetic(String), writePushPop(),
 *
 *                Private:  writeAdd(), writePushConstant(), genStatic(),
 *                          writePushD(), writePopD(),
 *
 * Notes:         //    TODO - WRITE METHODS NEED TESTING
 *
 ************************************************************************************/
package edu.miracosta.cs220;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class CodeWriter {

    /**********************
     * Instance Variables *
     **********************/
    private PrintWriter outputFile;
    private String curVMfile;   //  name of current .vm file being translated

    /****************
     * Constructors *
     ****************/

    /**
     * Opens output file/stream and prepares to write to it.
     *
     * PRECONDITION:    outFileName's extension is .asm
     * POSTCONDITION:   the file stream for outFileName is ready to be written to
     *                  or a FileNotFoundException has been thrown
     *
     * @param   outFileName -   the name of the file to be opened and written to
     *
     * @throws  FileNotFoundException   -   if outFileName could not be opened or == null
     */
    public CodeWriter(String outFileName) throws FileNotFoundException {
        if (outFileName != null) {
            outputFile = new PrintWriter(new File(outFileName));
        } else {
            throw new FileNotFoundException("No output file name provided to CodeWriter constructor.");
        }
    }

    /**
     * Opens output file/stream, prepares to write to it, and informs the code writer that the
     * translation of a VM file has started.
     *
     * PRECONDITION:    outFileName's extension is .asm
     * POSTCONDITION:   the file stream for outFileName is ready to be written to
     *                  or a FileNotFoundException has been thrown
     *
     * @param   outFileName -   the name of the file to be opened and written to
     * @param   newVMfile   -   the name of the VM file being translated
     *
     * @throws  FileNotFoundException   -   if outFileName could not be opened or == null
     */
    public CodeWriter(String outFileName, String newVMfile) throws FileNotFoundException {
        this(outFileName);
        setFileName(newVMfile);
    }

    /**************************
     * General Public Methods *
     **************************/

    /**
     * Informs the code writer that the translation of a new VM file has started.
     *
     * PRECONDITION:    newVMfile has been successfully opened and is ready to be translated
     * POSTCONDITION:   curVMfile has been updated
     *
     * @param   newVMfile   -   the name of the new VM file being translated
     */
    public void setFileName(String newVMfile) {
        int extIndex = newVMfile.indexOf(".vm");
        if (extIndex != -1) {
            //  If an extension is present, get rid of it
            curVMfile = newVMfile.substring(0, extIndex);
        }
    }

    /**
     * Closes the output file.
     *
     * PRECONDITION:    N/A
     * POSTCONDITION:   outputFile has been closed or was already closed
     */
    public void close() {
        try {
            outputFile.close();
        } catch (IllegalStateException e) {
            //  If already closed, do nothing
            return;
        }
    }

    /**************************
     * Public Writing Methods *
     **************************/

    /**
     * Writes the assembly code that is the translation of the given arithmetic command.
     *
     * PRECONDITION:    the command is a C_ARITHMETIC command
     * POSTCONDITION:   the translated assembly code has been written to the output file
     *
     * @param   command -   the given arithmetic command
     */
    public void writeArithmetic(String command) {
        switch(command) {
            case "add":
                writeBinaryOp();
                outputFile.println("M=M+D");
                break;
            case "sub":
                writeBinaryOp();
                outputFile.println("M=M-D");
                break;
            case "and":
                writeBinaryOp();
                outputFile.println("M=M&D");
                break;
            case "or":
                writeBinaryOp();
                outputFile.println("M=M|D");
                break;
            case "not":
                writeUnaryOp();
                break;
            case "neg":
                writeUnaryOp();
                outputFile.println("M=M+1");
                break;
            case "eq":
                break;
            case "lt":
                break;
            case "gt":
                break;
        }
    }

    /**
     * Writes the assembly code that is the translation of the given command, where command
     * is either C_PUSH or C_POP.
     *
     * PRECONDITION:    command is C_PUSH or C_POP
     * POSTCONDITION:   the translated assembly code has been written to the output file
     *
     * @param   command -   a Command of type C_PUSH or C_POP
     * @param   segment -   a virtual memory segment
     * @param   index   -   determines which address to access within segment
     */
    public void writePushPop(Parser.Command command, String segment, int index) {

    }

    /***************************
     * Private Writing Helpers *
     ***************************/

    /**
     * Writes assembly code to pop the top-most value off the stack and store it in the D-Register
     * and simultaneously update the stack pointer (SP).
     *
     * PRECONDITION:    a value needs to be popped off the stack and stored into the D-Register
     * POSTCONDITION:   commands to put the top of the stack in the D-Register have been written to the output file
     */
    private void writePopD() {
        outputFile.println("@SP");
        outputFile.println("AM=M-1");
        outputFile.println("D=M");
    }

    /**
     * Writes assembly code to push a value from the D-Register onto the top of the stack
     * and simultaneously update the stack pointer (SP).
     *
     * PRECONDITION:    a value needs to be pushed onto the stack from the D-Register
     * POSTCONDITION:   commands to push the value in the D-Register to the top of the stack
     *                  have been written to the output file
     */
    private void writePushD() {
        outputFile.println("@SP");
        outputFile.println("AM=M+1");
        outputFile.println("A=A-1");
        outputFile.println("M=D");
    }

    /**
     * Writes assembly code to push a value from the D-Register onto the top of the stack
     * and simultaneously update the stack pointer (SP). [writePopD()] Then, it updates the
     * A-Register to the next upper-most value on the stack to perform the binary operation.
     *
     * PRECONDITION:    a binary operation will be performed on the top two values on the stack
     *                  (used for ADD, SUB, AND, & OR C_ARITHMETIC commands)
     * POSTCONDITION:   the A-Register holds the address in which to store the result of the
     *                  desired binary operation
     */
    private void writeBinaryOp() {
        writePopD();
        outputFile.println("A=A-1");
    }

    /**
     * Writes assembly code to perform the beginning stage of the 2's complement on the top-most
     * value of the stack. The stack pointer (SP) does not need to be adjusted, as the result will
     * be stored in the same location.
     *
     * PRECONDITION:    a unary operation will be performed on the top-most value on the stack
     *                  (used for NEG and NOT C_ARITHMETIC commands)
     * POSTCONDITION:   the NOT operation has been fully translated, and the NEG operation can
     *                  finish the 2's complement on the address stored in A. (add 1 to M)
     */
    private void writeUnaryOp() {
        outputFile.println("@SP");
        outputFile.println("A=M-1");
        outputFile.println("M=!M");
    }
}
