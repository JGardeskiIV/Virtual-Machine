package edu.miracosta.cs220;

import java.io.FileNotFoundException;

public class VirtualMachine {

    public static void main(String[] args) {
        try {
            Parser parser = new Parser("Test.vm");
            while(parser.hasMoreCommands()) {
                parser.advance();
                System.out.println("Line " + parser.getLineNumber() + ":");
                System.out.println(parser.getCommand().equals("") ? "Command = Empty String" : parser.getCommand());
                System.out.println(parser.getArg1().equals("") ? "Arg 1 = Empty String" : parser.getArg1());
                System.out.println(parser.getArg2().equals("") ? "Arg 2 = Empty String" : parser.getArg2());
                System.out.println();
            }
        } catch (FileNotFoundException e ) {

        }
    }
}
