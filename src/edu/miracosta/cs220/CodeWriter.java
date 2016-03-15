/************************************************************************************
 *
 * Class name:    CodeWriter
 * Description:   Translates VM commands into Hack assembly code.
 *
 * History:       Mar. 13, J, author, stubs & documentation based off of Ch. 7 UML
 *                Mar. 13, J, defined constructors, basic public & private methods
 *                Mar. 14, J, completed & refactored part 1 write methods
 *
 * Methods:       Public:   CodeWriter(String), CodeWriter(String, String),
 *                          setFileName(String), close(),
 *                          writeArithmetic(String),
 *                          writePushPop(Parser.Command, String, int)
 *
 *                Private:  initTranslator(), getBranchLabel(), getStaticLabel(int),
 *                          writeIndexOffset(String, int),
 *                          writePopD(), writePopToMem(String, int), writePopToStatic(int),
 *                          writePushD(), writePushConstant(int),
 *                          writePushMemory(String, int), writePushStatic(int),
 *                          writeBinaryOp(String), writeUnaryOp, writeInequality(String)
 *
 ************************************************************************************/
package edu.miracosta.cs220;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;

public class CodeWriter {
    /*******************************
     * Class Variables & Constants *
     *******************************/
    //  Stores VM commands & memory segments as keys and their assembly translations as values
    private static HashMap<String, String> translator;
    //  Used in generating branch labels (_#)
    private static int labelCounter = 1;

    private static final String POINTER_LOC = "3";
    private static final String TEMP_LOC = "5";

    /**********************
     * Instance Variables *
     **********************/
    private PrintWriter outputFile;
    private String curVMfileName;   //  name of current .vm file being translated

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
        initTranslator();   //  Build VM commands/segments -> assembly variants
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
     * @param   newVMfileName   -   the name of the VM file being translated
     *
     * @throws  FileNotFoundException   -   if outFileName could not be opened or == null
     */
    public CodeWriter(String outFileName, String newVMfileName) throws FileNotFoundException {
        this(outFileName);
        setFileName(newVMfileName);
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
     * @param   newVMfileName   -   the name of the new VM file being translated
     */
    public void setFileName(String newVMfileName) {
        int extIndex = newVMfileName.indexOf(".vm");
        if (extIndex != -1) {
            //  If an extension is present, get rid of it
            curVMfileName = newVMfileName.substring(0, extIndex);
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
        //  Command is not case-sensitive
        command = command.toLowerCase();
        switch(command) {
            case "add":
            case "sub":
            case "and":
            case "or":
                writeBinaryOp(translator.get(command));
                break;
            case "not":
                writeUnaryOp();
                break;
            case "neg":
                writeUnaryOp();
                //  Finish 2's Complement (not + 1)
                outputFile.println("M=M+1");
                break;
            case "eq":
            case "lt":
            case "gt":
                //  REMINDER: translator HashMap pairs lt & gt with the not of the condition [!(lt) = JGT, !(gt) = JLT]
                writeInequality(translator.get(command));
                break;
        }
    }

    /**
     * Writes the assembly code that is the translation of the given command, where command
     * is either C_PUSH or C_POP.
     *
     * PRECONDITION:    the VM command is C_PUSH or C_POP
     * POSTCONDITION:   the translated assembly code has been written to the output file
     *
     * @param   command -   a Command of type C_PUSH or C_POP
     * @param   segment -   a virtual memory segment
     * @param   index   -   determines which address to access within segment
     */
    public void writePushPop(Parser.Command command, String segment, int index) {
        segment = segment.toLowerCase();
        if (command == Parser.Command.C_PUSH) {
            switch(segment) {
                case "constant":
                    writePushConstant(index);
                    break;
                case "static":
                    writePushStatic(index);
                    break;
                default:
                    //  Catches memory segments: local, argument, this, that, pointer, & temp
                    //  REMINDER:   translator HashMap handles pointer vs. address notation
                    //              ->  difference between local..that & pointer/temp
                    writePushMemory(translator.get(segment), index);
                    break;
            }
        } else {
            //  C_POP
            if (segment.equals("static")) {
                writePopToStatic(index);
            } else {
                //  Catches memory segments: local, argument, this, that, pointer, & temp
                //  REMINDER:   translator HashMap handles pointer vs. address notation
                //              ->  difference between local..that & pointer/temp
                writePopToMem(translator.get(segment), index);
            }
        }
    }
    
    /**************************
     * General Helper Methods *
     **************************/

    /**
     * Builds out the translator HashMap to hold VM commands & memory segments and
     * their implementation in assembly.
     *
     * NOTE:    Accounts for pointer [RAM[base] + i] vs. value [address + i] notation
     *          AND
     *          Pairing the "lt" or "gt" arithmetic commands with the "not" of the
     *          jump condition to be used in assembly.
     *          ->  instead of testing x < y as x - y < 0,
     *              test as y - x > 0
     *
     *          **  Saves two lines of assembly when translated.
     *
     * PRECONDITION:    N/A
     * POSTCONDITION:   translator contains all predefined translations
     */
    private void initTranslator() {
        translator = new HashMap<>();
        //  Add all binary C_ARITHMETIC commands and their operators/jump codes
        translator.put("add", "+");
        translator.put("sub", "-");
        translator.put("and", "&");
        translator.put("or",  "|");
        //  A "not" will be applied to the jump condition in "eq", "lt", and "gt"
        //  Thus, if command is "<" or ">", the jump condition is reversed.
        translator.put("eq",  "JEQ");
        translator.put("lt",  "JGT");   //  !(lt) = JGT
        translator.put("gt",  "JLT");   //  !(gt) = JLT

        //  Add all virtual memory segments (except for constant)
        //  local..that -> [base + i] = [RAM[address] + i]
        translator.put("local", "LCL");
        translator.put("argument", "ARG");
        translator.put("this", "THIS");
        translator.put("that", "THAT");
        //  pointer, temp -> [address + i]
        translator.put("pointer", "3");
        translator.put("temp", "5");
    }
    
    /**
     * Generates a label for branching in assembly in the format _#.
     * 
     * PRECONDITION:    no other method adjusts the value of labelCounter
     * POSTCONDITION:   labelCounter represents the next available label,
     *                  and the generated label does not specify an assembly instruction
     */
    private String getBranchLabel() {
        return "_" + labelCounter++;
    }
    
    /**
     * Generates a static label in the format fileName.index that will
     * access the static virtual memory segment.
     * 
     * PRECONDITION:    curVMfileName is not null -> a .vm file is being translated
     * POSTCONDITION:   the generated label does not specify an assembly instruction
     * 
     * @param   index   -   the index of the static segment to access
     */
    private String getStaticLabel(int index) {
        return curVMfileName + "." + index;
    }
    
    /****************************
     * Assembly Writing Helpers *
     ****************************/

    //  Push & Pop Helpers

    /**
     * Generates assembly code to handle the pointer vs. value notation difference in
     * handling the local..that vs. pointer/temp virtual segments.
     *
     * NOTE:    Loads the segment, stores the address OR value into D, and loads the
     *          index into A. STOPS HERE, as push/pop handle the sum differently.
     *
     * PRECONDITION:    the VM command is C_PUSH or C_POP (writePushPop() was called)
     * POSTCONDITION:   assembly commands to access the appropriate segment and "element"
     *                  have been written to the output file
     *
     * @param   segment -   the VM virtual memory segment to be accessed
     * @param   index   -   the specific address within the segment to access
     */
    private void writeIndexOffset(String segment, int index) {
        outputFile.println("@" + segment);
        if (segment.equals(POINTER_LOC) || segment.equals(TEMP_LOC)) {
            //  Pointer or Temp = value, not an address
            outputFile.println("D=A");
        } else {
            //  All other segments = pointer notation (base + i)
            outputFile.println("D=M");
        }
        outputFile.println("@" + index);
    }

    /**
     * Writes assembly code to pop the top-most value off the stack and store it in the D-Register.
     *
     * PRECONDITION:    the VM command is C_POP, and
     *                  a value needs to be popped off the stack and stored into the D-Register
     * POSTCONDITION:   commands to put the top of the stack in the D-Register have been written to the output file
     */
    private void writePopD() {
        outputFile.println("@SP");
        outputFile.println("AM=M-1");
        outputFile.println("D=M");
    }

    /**
     * Writes assembly code to pop the top-most value off the stack and store it into
     * the virtual segment specified at the indicated index.
     *
     * PRECONDITION:    the VM command is C_POP, and
     *                  a value needs to be popped off the stack and stored into memory
     * POSTCONDITION:   assembly commands to put the top of the stack in segment[index]
     *                  have been written to the output file
     *
     * @param   segment -   the VM virtual memory segment to be accessed
     * @param   index   -   the specific address within the segment to access
     */
    private void writePopToMem(String segment, int index) {
        writeIndexOffset(segment, index);
        outputFile.println("D=D+A");
        outputFile.println("@" + TEMP_LOC);
        outputFile.println("M=D");
        writePopD();
        outputFile.println("@" + TEMP_LOC);
        outputFile.println("A=M");
        outputFile.println("M=D");
    }

    /**
     * Writes assembly code to pop the top-most value off the stack and store it into
     * the static segment at the specified index.
     *
     * PRECONDITION:    the VM command is C_POP, and
     *                  a value needs to be popped off the stack and stored into the static memory segment
     * POSTCONDITION:   assembly commands to put the top of the stack into static[index]
     *                  have been written to the output file
     *
     * @param   index   -   the specific index to access within the static segment
     */
    private void writePopToStatic(int index) {
        writePopD();
        outputFile.println("@" + getStaticLabel(index));    //  label of form fileName.index
        outputFile.println("M=D");
    }

    /**
     * Writes assembly code to push a value from the D-Register onto the top of the stack.
     *
     * PRECONDITION:    the VM command is C_PUSH, and
     *                  a value needs to be pushed onto the stack from the D-Register
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
     * Writes assembly code to push a constant onto the top of the stack.
     *
     * PRECONDITION:    the VM command is C_PUSH, and constant is an integer
     * POSTCONDITION:   commands to push constant onto the stack have been written to the output file
     *
     * @param   constant    -   an integer to push onto the stack
     */
    private void writePushConstant(int constant) {
        outputFile.println("@" + constant);
        outputFile.println("D=A");
        writePushD();
    }

    /**
     * Writes assembly code to push a value from the indicated index within the specified
     * virtual memory segment onto the top of the stack.
     *
     * PRECONDITION:    the VM command is C_PUSH, and
     *                  a value needs to be pushed onto the stack from memory
     * POSTCONDITION:   assembly commands to push a value onto the stack from segment[index]
     *                  have been written to the output file
     *
     * @param   segment -   the VM virtual memory segment to be accessed
     * @param   index   -   the specific address within the segment to access
     */
    private void writePushMemory(String segment, int index) {
        writeIndexOffset(segment, index);
        outputFile.println("A=D+A");
        outputFile.println("D=M");
        writePushD();
    }

    /**
     * Writes assembly code to push a value from the indicated index within the static
     * virtual memory segment onto the top of the stack.
     *
     * PRECONDITION:    the VM command is C_PUSH, and
     *                  a value needs to be pushed onto the stack from the static memory segment
     * POSTCONDITION:   assembly commands to push a value onto the stack from static[index]
     *                  have been written to the output file
     *
     * @param   index   -   the specific index to access within the static segment
     */
    private void writePushStatic(int index) {
        outputFile.println("@" + getStaticLabel(index));
        outputFile.println("D=M");
        writePushD();
    }

    //  C_ARITHMETIC Helpers

    /**
     * Writes assembly code to push a value from the D-Register onto the top of the stack
     * and simultaneously update the stack pointer (SP). [writePopD()] Then, it updates the
     * A-Register to the next upper-most value on the stack to perform the binary operation.
     *
     * PRECONDITION:    a binary operation will be performed on the top two values on the stack
     *                  (used for ADD, SUB, AND, & OR C_ARITHMETIC commands)
     * POSTCONDITION:   the A-Register holds the address in which to store the result of the
     *                  desired binary operation
     *
     * @param   operator    -   the operator for the desired operation [+, -, &, |]
     */
    private void writeBinaryOp(String operator) {
        writePopD();
        outputFile.println("A=A-1");
        outputFile.println("M=M" + operator + "D");
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
    
    /**
     * Writes assembly code to perform an inequality ("eq", "lt", or "gt") operation
     * on the top two values on the stack.
     * 
     * PRECONDITION:    the VM command is an eq, lt, or gt C_ARITHMETIC command
     * POSTCONDITION:   translated assembly code has been written to the output file
     * 
     * @param   jump -   the VM command to perform [determines the assembly jump condition]
     */
    private void writeInequality(String jump) {
        String label1 = getBranchLabel();
        String label2 = getBranchLabel();
        //  Construct assembly code
        writePopD();    //  SP is updated to the address of SP - 1
        outputFile.println("A=A-1");
        outputFile.println("D=D-M");        //  D = value of SP - 1 - value of SP - 2
        outputFile.println("@" + label1);
        outputFile.println("D;" + jump);    //  if condition is true, jump to label1 branch
        outputFile.println("D=0");          //  D = false
        outputFile.println("@" + label2);   
        outputFile.println("0;JMP");        
        outputFile.println("(" + label1 + ")"); //  condition is true branch
        outputFile.println("D=-1");             //  D = true
        outputFile.println("(" + label2 + ")"); //  condition is false branch
        outputFile.println("@SP");
        outputFile.println("A=M-1");
        outputFile.println("M=D");          //  address of (original) SP - 2 = result of comparison (D)
    }
}
