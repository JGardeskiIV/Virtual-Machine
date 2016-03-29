/************************************************************************************
 *
 * Class name:    CodeWriter
 * Description:   Translates VM commands into Hack assembly code.
 *
 * History:       Mar. 13, J, author, stubs & documentation based off of Ch. 7 UML
 *                Mar. 13, J, defined constructors, basic public & private methods
 *                Mar. 14, J, completed & refactored part 1 write methods
 * 				  Mar. 17, J, fleshed out UML (stubs) for Part 2 of the VM,
 * 							, and fixed static labels having the full path name
 *				  Mar. 18, J, filled in stubs - 1st attempt at Part 2 translation
 *				  Mar. 20, J, finalized write methods & label translation/handling
 *				  Mar. 27, J, added badly-needed comments, updated documentation
 *
 * Methods:       Public:   CodeWriter(File, String, boolean),
 *                          setFileName(String), close(),
 *                          writeArithmetic(String),
 *                          writePushPop(Parser.Command, String, int),
 * 							writeLabel(String), writeGoto(String),
 * 							writeIfGoto(String), writeCall(String, int),
 * 							writeReturn(), writeFunction(String, int)
 *
 *                Private:  initTranslator(),
 *							getBranchLabel(), getStaticLabel(int), getProperLabel(String),
 *							writeBootstrap(), writeIndexOffset(String, int),
 *							writePushPointer(String), writeRestorePointer(String),
 *                          writePopD(), writePopToMem(String, int), writePopToStatic(int),
 *                          writePushD(), writePushConstant(int),
 *                          writePushMemory(String, int), writePushStatic(int),
 *                          writeBinaryOp(String), writeUnaryOp(), writeInequality(String)
 *
 ************************************************************************************/
package edu.miracosta.cs220;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;

class CodeWriter {
    /*******************************
     * Class Variables & Constants *
     *******************************/
    //  Stores VM commands & memory segments as keys and their assembly translations as values
    private static HashMap<String, String> translator;

    //  Used in generating branch labels (_#)
    private static int labelCounter = 1;

    //	Starting memory addresses for the temp and pointer virtual segments
    private static final String POINTER_LOC = "3";
    private static final String TEMP_LOC = "5";

    /**********************
     * Instance Variables *
     **********************/
    private PrintWriter outputFile;
    private String curVMfileName;   //  name of current .vm file being translated
    private String curFunction;		//	name of the current function

    //	line number in .asm file being written
    //	NOTE:	only increment for A & C-Instructions!
    private int romAddress;

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
     * @param	dir		-	the name of the directory to write the file to
     * @param   outFileName -   the name of the file to be opened and written to
     * @param	bootstrap	-	true to include bootstrap code in outFileName,
     *							false to not include it
     *
     * @throws  FileNotFoundException   -   if outFileName could not be opened or == null
     */
    CodeWriter(File dir, String outFileName, boolean bootstrap) throws FileNotFoundException {
        if (outFileName == null) {
            throw new FileNotFoundException("File name not specified in CodeWriter constructor.");
        } else {
            //	Create the .asm file in the specified (or current) directory [dir]
            outputFile = new PrintWriter(new File(dir, outFileName));
            curVMfileName = outFileName;
        }
        curFunction = "";
        romAddress = 0;
        initTranslator();   //  Build VM commands/segments -> assembly variants
        if (bootstrap) {
            writeBootstrap();
        }
    }

    /**************************
     * General Public Methods *
     **************************/

    /**
     * Informs the code writer that the translation of a new VM file has started.
     *
     * PRECONDITION:    newVMfile has been successfully opened and is ready to be translated
     * POSTCONDITION:   curVMfile has been updated to the name of the file (no .ext)
     *
     * @param   newVMfileName   -   the name of the new VM file being translated
     */
    void setFileName(String newVMfileName) {
        int extIndex = newVMfileName.indexOf(".");
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
    void close() {
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
    void writeArithmetic(String command) {
        //  Command is not case-sensitive
        command = command.toLowerCase();
        switch(command) {
            case "add":
            case "sub":
            case "and":
            case "or":
                //	Translation is the same for binary operators;
                //	Just get the right symbol!
                writeBinaryOp(translator.get(command));
                break;
            case "not":
                writeUnaryOp();
                break;
            case "neg":
                writeUnaryOp();
                //  Finish 2's Complement (not + 1)
                outputFile.println("M=M+1");
                romAddress++;
                break;
            case "eq":
            case "lt":
            case "gt":
                //  REMINDER: translator HashMap pairs lt & gt with the not of the condition [!(lt) = JGT, !(gt) = JLT]
                //			  since assembly translation subtracts the top two elements on the stack "in reverse"
                //
                //	** Again, binary ops = same translation except for the symbol!
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
    void writePushPop(Parser.Command command, String segment, int index) {
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

    /**
     * Writes assembly code that effects the VM label command (label).
     *
     * PRECONDITION:	the VM command is C_LABEL
     * POSTCONDITION:	an assembly label has been written to the output file
     *
     * @param	label	-	the symbol/label to use in assembly
     */
    void writeLabel(String label) {
        //	Don't update romAddress!
        outputFile.println("(" + getProperLabel(label) + ")");
    }

    /**
     * Writes assembly code that effects the goto VM command as
     * an unconditional jump in assembly.
     *
     * PRECONDITION:	the VM command is C_GOTO
     * POSTCONDITION:	assembly code to jump to label has been written
     * 					to the output file
     *
     * @param	label	-	the symbol/label to jump to in assembly
     */
    void writeGoto(String label) {
        outputFile.println("@" + getProperLabel(label));
        outputFile.println("0;JMP");
        romAddress += 2;
    }

    /**
     * Writes assembly code that effects the if-goto VM command.
     * ->	if the top of the stack holds true, then jump to label
     *
     * PRECONDITION:	the VM command is C_IF
     * POSTCONDITION:	assembly code to jump to label if the top of the
     * 					stack holds true has been written to the output file
     *
     * @param	label	-	the symbol/label to jump to in assembly if
     * 						the top of the stack is true
     */
    void writeIfGoto(String label) {
        writePopD();
        outputFile.println("@" + getProperLabel(label));
        outputFile.println("D;JNE");	//	0 = false = jump if not 0
        romAddress += 2;
    }

    /**
     * Writes assembly code that effects the call VM command in assembly.
     * ->	Saves the current function's frame to the stack, repositions
     * 		the local [LCL] and argument [ARG] pointers, and transfers
     * 		control (goto/jumps) to the called function.
     *
     * PRECONDITION:	the VM command is C_CALL
     * POSTCONDITION:	assembly code to transfer control to the CALLED
     * 					function has been written to the output file
     *
     * @param	functionName	-	the name of the function being CALLED
     * @param	numArgs			-	the number of arguments that functionName
     * 								needs [pushed onto stack by compiler]
     */
    void writeCall(String functionName, int numArgs) {
		/*	The return address should equal the romAddress after the call
		 *	setup and control transfer has been completed. [Represented by
		 *	the ending (RIP##) label]. This is the value pushed onto the stack.
		 *
		 *	->	1.	writePushConstant & writePushPointer contain 6 commands each;
		 *			add the 10 commands written afterwards = (romAddress + 40)
		 *		2.	All write methods update romAddress, so only update actual
		 *			saved value (+ 10) after retAddr is pushed onto the stack
		 *			and all needed write methods have been called.
		 */
        writePushConstant(romAddress + 40);	//	Push RIP (Return Insertion Point)
        writePushPointer("LCL");			//	Save the current frame's state
        writePushPointer("ARG");
        writePushPointer("THIS");
        writePushPointer("THAT");
        outputFile.println("@SP");
        outputFile.println("D=M");					//	D = SP (address)
        outputFile.println("@LCL");
        outputFile.println("M=D");					//	Reposition LCL [LCL = SP]
        outputFile.println("@" + (numArgs + 5));	//	A = ARG adjustment [retAddr + frame + #args]
        outputFile.println("D=D-A");				//	D = SP - ARG adjustment
        outputFile.println("@ARG");
        outputFile.println("M=D");					//	ARG = SP - (numArgs + 5)
        outputFile.println("@" + functionName);
        outputFile.println("0;JMP");				//	Transfer control to functionName
        //	Now, update romAddress
        romAddress += 10;
        //	But don't count this label in the update
        outputFile.println("(RIP" + romAddress + ")");
    }

    /**
     * Writes assembly code that effects the return VM command.
     * ->	Reposition the return value onto the top of the stack,
     * 		restore the frame of the calling function, and transfer
     * 		control (goto/jump) back to the calling function via the
     * 		return address saved to the stack.
     *
     * PRECONDITION:	the VM command is C_RETURN
     * POSTCONDITION:	assembly code to return control to the CALLING
     * 					function has been written to the output file
     */
    void writeReturn() {
        //	Save the return address in a temporary variable
        outputFile.println("@LCL");
        outputFile.println("D=M");		//	D = LCL (address)
        outputFile.println("@5");
        outputFile.println("A=D-A");	//	A = LCL - 5 (address)
        outputFile.println("D=M");		//	D = Value AT [LCL - 5]
        outputFile.println("@R15");
        outputFile.println("M=D");		//	R15 = Value AT [LCL - 5] (save retAddr)
        writePopD();
        outputFile.println("@ARG");
        outputFile.println("A=M");		//	*ARG, not ARG (RAM[RAM[ARG]], not RAM[ARG])
        outputFile.println("M=D");		//	*ARG = pop() - Reposition return value for caller
        outputFile.println("@ARG");
        outputFile.println("D=M+1");	//	D = ARG (address) + 1
        outputFile.println("@SP");
        outputFile.println("M=D");		//	SP = ARG (address) + 1 - Restore SP of caller
        writeRestorePointer("THAT");	//	Restore the frame/state of the caller
        writeRestorePointer("THIS");
        writeRestorePointer("ARG");
        writeRestorePointer("LCL");
        outputFile.println("@R15");
        outputFile.println("A=M");
        outputFile.println("0;JMP");	//	Jump to the saved return address
        //	Only update raw println() calls
        romAddress += 17;
    }

    /**
     * Writes assembly code that effects the function VM command in assembly.
     * ->	Writes an assembly label containing the functionName,
     * 		and assembly code to initialize numLocals local variables to 0
     * 		(pushing 0 numLocals times onto the stack) to outputFile.
     *
     * PRECONDITION:	the VM command is C_FUNCTION
     * POSTCONDITION:	assembly code to declare functionName and initialize
     * 					numLocals local variables has been written to the output file
     *
     * @param	functionName	-	the name of the function being DECLARED
     * @param	numLocals		-	the number of local variables in functionName
     * 								[to be initialized to 0]
     */
    void writeFunction(String functionName, int numLocals) {
        outputFile.println("(" + functionName + ")");
        curFunction = functionName;
        for(int i = 0; i < numLocals; i++) {
            outputFile.println("@SP");
            outputFile.println("AM=M+1");
            outputFile.println("A=A-1");
            outputFile.println("M=0");
            romAddress += 4;
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

    /**
     * Returns a given label in the format functionName$label.
     *
     * PRECONDITION:	N/A
     * POSTCONDITION:	label is returned in the form functionName$label
     *
     * @param	label	-	the symbol/label to use in assembly
     * @return			-	label in the format functionName$label
     */
    private String getProperLabel(String label) {
        return curFunction + "$" + label;
    }

    /****************************
     * Assembly Writing Helpers *
     ****************************/

    /**
     * Writes assembly code that effects the VM initialization, also
     * called bootstrap code. Thid code must be placed at the beginning
     * of the output file.
     *
     * PRECONDITION:	the output file has been successfully opened
     * POSTCONDITION:	assembly bootstrap code has been written to the output file
     */
    private void writeBootstrap() {
        outputFile.println("@256");
        outputFile.println("D=A");
        outputFile.println("@SP");
        outputFile.println("M=D");
        romAddress += 4;
        writeCall("Sys.init", 0);
    }

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
        romAddress += 3;
    }

    /**
     * Pushes the value of a virtual memory segment (pointer) onto the stack.
     * To be used to save the state of the current frame's pointers.
     *
     * PRECONDITION:	the VM command is C_CALL
     * POSTCONDITION:	assembly code to push the value of the given pointer
     *					onto the stack has been written to the output file
     *
     * @param	pointer	-	the assembly symbol for the virtual memory segment
     *						whose value will be pushed onto the stack
     */
    private void writePushPointer(String pointer) {
        outputFile.println("@" + pointer);
        outputFile.println("D=M");
        writePushD();
        romAddress += 2;
    }

    /**
     * Restores the specified virtual memory segment of the caller.
     *
     * PRECONDITION:	the VM command is C_RETURN, and LCL's address has
     *					already been used to save the return address
     * POSTCONDITION:	assembly code to restore the value of the given pointer
     *					has been written to the output file
     *
     * @param	pointer	-	the assembly symbol for the virtual memory segment
     *						whose value will be restored
     */
    private void writeRestorePointer(String pointer) {
		/*	The address of LCL is 1 above the saved state of the calling function.
		 *	Thus, it can be used as a pseudo-SP pointer to restore the THAT, THIS,
		 *	ARG, and finally LCL itself pointers to restore the frame of the caller.
		 *
		 *	As long as the return value is saved FIRST, LCL's address can be manipulated
		 *	at will until it has at last restored itself.
		 */
        outputFile.println("@LCL");
        outputFile.println("AM=M-1");		//	A & RAM[LCL] = RAM[LCL] - 1
        outputFile.println("D=M");			//	D = *(LCL - 1)
        outputFile.println("@" + pointer);
        outputFile.println("M=D");			//	RAM[pointer] = *(LCL - 1) - pointer restored
        romAddress += 5;
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
        romAddress += 3;
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
        outputFile.println("@R14");
        outputFile.println("M=D");
        writePopD();
        outputFile.println("@R14");
        outputFile.println("A=M");
        outputFile.println("M=D");
        romAddress += 6;
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
        romAddress += 2;
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
        romAddress += 4;
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
        romAddress += 2;
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
        romAddress += 2;
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
        romAddress += 2;
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
        romAddress += 2;
    }

    /**
     * Writes assembly code to perform the beginning stage of the 2's complement on the top-most
     * value of the stack [negation]. The stack pointer (SP) does not need to be adjusted, as the
     * result will be stored in the same location.
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
        romAddress += 3;
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
        //	Get unique labels for assembly branching
        String label1 = getBranchLabel();
        String label2 = getBranchLabel();
        //  Construct assembly code
        writePopD();    //  SP is updated to the address of SP - 1
        outputFile.println("A=A-1");
        outputFile.println("D=D-M");        //  D = value of (SP - 1) - value of (SP - 2)
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
        romAddress += 11;
    }
}


