
package linking_loader;

import java.io.*;
import java.util.*;

public class Linking_Loader {
    static List<String> lines = new ArrayList<>();      //Array list of HTE records
    static String ESTAB[][] = new String[3][4];     //Array representing ESTAB
    static String memory[][] = new String[30][16];     //Array representing memeory
    
    public static void main(String[] args) throws FileNotFoundException {
        File file = new File("inLoaders.txt");      //Open file
        Scanner s = new Scanner(file);      //Open scanner
        
        //Count number of lines in the file (except blank)
        while (s.hasNextLine()) {
            String currentLine = s.nextLine().trim();
            if (!currentLine.isBlank()) {
                lines.add(currentLine);
            }
        }
        s.close();      //Close scanner
        
        int prog1 = 0, prog2 = 0, prog3 = 0;        //Default values
        String prog1Name = "", prog2Name = "", prog3Name = "";      //Default names
        String prog1Addr = "000000", prog2Addr = "000000", prog3Addr = "000000";      //Default addresses
        String prog1Len = "000000", prog2Len = "000000", prog3Len = "000000";      //Default lengths
                
        //Extract starting index & details of each program
        for (int i=0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("H")) {     //Find H record
                if (line.contains("PROG1")) {
                    prog1 = i;      //Get PROG1 start index
                    prog1Name = "PROG1";        //Assign PROG1 name
                    prog1Addr = line.substring(10, 17);      //Extract PROG1 starting address
                    prog1Len = line.substring(18);      //Extract PROG1 length
                } else if (line.contains("PROG2")) {
                    prog2 = i;      //Get PROG2 start index
                    prog2Name = "PROG2";        //Assign PROG2 name
                    prog2Addr = line.substring(10, 17);      //Extract PROG2 starting address
                    prog2Len = line.substring(18);      //Extract PROG2 length
                } else if (line.contains("PROG3")) {        
                    prog3 = i;      //Get PROG3 start index
                    prog3Name = "PROG3";        //Assign PROG3 name
                    prog3Addr = line.substring(10, 17);      //Extract PROG3 starting address
                    prog3Len = line.substring(18);      //Extract PROG3 length
                }
            }
        }
        
        //Create ESTAB
        addToESTAB(0,prog2, prog2Name, prog2Addr, prog2Len);
        addToESTAB(1,prog3, prog3Name, prog3Addr, prog3Len);
        addToESTAB(2,prog1, prog1Name, prog1Addr, prog1Len);
        
        //Load programs to memory
        linking(prog2, prog2Name);
        linking(prog3, prog3Name);
        linking(prog1, prog1Name);
               
        printESTAB();       //Print ESTAB
        printMemory();      //Print memory
    }
    
    //Method to add control sections to ESTAB
    public static void addToESTAB(int order, int prog, String progName, String progAddr, String progLen){
        ESTAB[order][0] = progName;     //Add program name to ESTAB
        ESTAB[order][1] = "";       //Symbols placeholder
        
        //Calculate starting address
        if(order == 0)      //First program loaded
            ESTAB[order][2] = Integer.toHexString(Integer.parseInt(progAddr, 16) + 0X4020).toUpperCase();
        else{   //Add length of previous program to its starting address to get current program starting address
            ESTAB[order][2] = Integer.toHexString(Integer.parseInt(ESTAB[order - 1][2], 16) + Integer.parseInt(ESTAB[order - 1][3], 16));
        }
        
        ESTAB[order][3] = progLen;      //Add program length to ESTAB
        
        //Move to D record
        int Drecord = prog + 1;     
        //Make sure it's a D record
        if(lines.get(Drecord).startsWith("D")){
            String line = lines.get(Drecord);
            for (int i=2; i+12<= line.length(); i+=14) {
                String symbol = line.substring(i, i + 6).trim();        //Extract symbol name
                String address = line.substring(i + 7, i + 13).trim();     //Extract symbol address
                
                //Remove X's from name
                while(symbol.startsWith("X")){      
                    symbol = symbol.substring(1).trim();
                }
                //Add control section address to symbol address
                address = Integer.toHexString(Integer.parseInt(ESTAB[order][2], 16) + Integer.parseInt(address, 16)).toUpperCase();  
                
                ESTAB[order][1] += symbol + "->" + address + "  ";      //Add symbol name & address to ESTAB
            }
        }
        else
            System.out.println("There are no external definitions in " + progName);     //Inform with lack of D record
    }
    
    //Method to run linking loader
    public static void linking(int prog, String progName){
        String progStart = "000000";        //Default program starting address
        List<String> mod_rec = new ArrayList<>();      //Array list of modification records

        //Get program starting address from ESTAB
        for(int i=0; i<ESTAB.length; i++){
            if(ESTAB[i][0].equals(progName)){
                progStart = Integer.toHexString(Integer.parseInt(ESTAB[i][2], 16) - 0x4020);
                break;
            }
        }
                
        int Trecord = prog + 3;     //Move to T record
        for(int i=Trecord; i < lines.size(); i++){       //Start at first T record
            String line = lines.get(i);
            //Stop at E record
            if(line.startsWith("E"))        
                break;
            
            //Extract M record
            if(line.startsWith("M"))
                mod_rec.add(line);
            
            //Extract T record
            if(line.startsWith("T")){
                //Extract T record starting address & add program starting address to it
                String start = line.substring(2, 8).trim();
                start = Integer.toHexString(Integer.parseInt(start, 16) + Integer.parseInt(progStart, 16)).toUpperCase(); 
                                
                String record = line.substring(12).trim();     //Extract T record
                                
                load(start, record);    //Load record to memory
            }
        }
        
        modify(mod_rec, prog, progStart, progName);    //Process modification records
    }
    
    //Method to load T record to memory
    public static void load(String start, String record){
        
        int startAddress = Integer.parseInt(start, 16);     //Convert starting address to decimal
        int row = startAddress / 16;        //Get row of starting address
        int col = startAddress % 16;     //Get column of starting address
        
        for(int i=0; i<record.length()-1; i+=2){
            //Prevent out-of-bounds access
            if(row >= memory.length || col >= memory[row].length)
                break;
            String data = record.substring(i, i + 2);       //Extract every 2 hexadecimal digits
            memory[row][col] = data;     //Add data to memory cell
            col++;       //Move to next column
            //If end of row, reset column & move to next row
            if(col >= 16){
                col = 0;
                row++;
            }
        }
    }
    
    //Method to process modification records
    public static void modify(List<String> mod_rec, int prog, String progStart, String progName){        
        //Extract important data from M record
        for(int i=0; i<mod_rec.size(); i++){
            String line = mod_rec.get(i).trim();
            
            //Extract M record address & add it to program starting address
            String addr = line.substring(2, 8).trim();
            addr = Integer.toHexString(Integer.parseInt(addr, 16) + Integer.parseInt(progStart, 16)).toUpperCase();
                        
            int row = Integer.parseInt(addr, 16) / 16;       //Get row of address
            int col = Integer.parseInt(addr, 16) % 16;        //Get column of address
            
            String operation = line.substring(11,12).trim();     //Extract M record operation
            String symbol = line.substring(12).trim();      //Extract M record symbol
            
            //Handle case if modification is numeric
            if(symbol.matches("\\d+")){     
                if(symbol.equals("01"))
                    symbol = progName;
                else{
                    int Rrecord = prog + 2;
                    if(lines.get(Rrecord).startsWith("R")){
                        String l = lines.get(Rrecord);
                        if(l.contains(symbol)){
                            symbol = l.substring(l.indexOf(symbol) + 2, l.indexOf(symbol) + 8);
                            while(symbol.startsWith("X")){
                                symbol = symbol.substring(1).trim();
                            }
                        }
                    }
                }
            }
                        
            String symAddr = findSymbolAddr(symbol);       //Find address of symbol in ESTAB
        
            //Find data to be modified in memory
            StringBuilder toBeModified = new StringBuilder();
            int r = row, c = col;       //Temp variables
            for(int j=0; j<3; j++){
                //If end of row, reset column & move to next row
                if(c == 16){
                    row++;
                    c = 0;
                }
                
                //Prevent out-of-bounds access
                if(r >= memory.length || c >= memory[r].length)
                    break;
            
                toBeModified.append(memory[r][c]);
                c++;
            }
            
            //Perform modification
            if(symAddr != null && toBeModified != null){
                int toBeModifiedValue = Integer.parseInt(toBeModified.toString(), 16);
                int symAddrValue = Integer.parseInt(symAddr, 16);
                int modifiedValue;
                
                switch (operation) {
                    case "+":       //Add address of modification symbol to data to be modified
                        modifiedValue = toBeModifiedValue + symAddrValue;
                        break;
                    case "-":       //Substract address of modification symbol from data to be modified
                        modifiedValue = toBeModifiedValue - symAddrValue;
                        modifiedValue &= 0xFFFFFF;      //In case it's negative
                        break;
                    default:        //Inform with error
                        modifiedValue = 0;
                        System.out.println("ERROR: Unable to extract modification record for " + progName);
                        break;
                }
                
                //Store modification in memory
                if(modifiedValue != 0){   
                    String modified = String.format("%06X", modifiedValue);
                    for(int j=0; j<modified.length()-1; j+=2){
                        //If end of row, reset column & move to next row
                        if(col == 16){
                            row++;
                            col = 0;
                        }
                        
                        //Prevent out-of-bounds access
                        if(row >= memory.length || col >= memory[row].length)
                            break;

                        String data = modified.substring(j, j + 2);       //Extract every 2 hexadecimal digits
                        memory[row][col] = data;     //Add data to memory cell
                        col++;       //Move to next column
                    }
                }
                else        //Inform with error
                    System.out.println("ERROR: Unable to parse modification.");

            }
            else        //Inform with error
                System.out.println("ERROR: Unable to find symbol in ESTAB.");
        }
    }
    
    //Method to find symbol's address in ESTAB
    public static String findSymbolAddr(String symbol){
        String address = "0";
        for(int i=0; i<ESTAB.length; i++){
            if(ESTAB[i][1].contains(symbol + "->")){      //Symbol is an external reference
                //Extract address of external reference (after ->)
                String s[] = ESTAB[i][1].split(" ");
                for(int j=0; j<s.length; j++){
                    if(s[j].stripLeading().startsWith(symbol + "->")){
                        address = s[j].substring(s[j].indexOf("->") + 2);
                        address = Integer.toHexString(Integer.parseInt(address, 16));
                    }
                }
            }
            else if(ESTAB[i][0].equals(symbol)){       //Symbol is control section name
                address = Integer.toHexString(Integer.parseInt(ESTAB[i][2], 16));      //Get address of control section
            }
        }
        return address;        
    }
   
    //Method to print ESTAB
    public static void printESTAB(){
        System.out.printf("|%-25s || %-24s || %-24s || %-24s |\n", "Control Section", "Symbol name & address", "Control section address", "Control section length");
        for(int i=0; i<ESTAB.length; i++){
            for(int j=0; j<4; j++){
                System.out.printf("|%-25s |", ESTAB[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }
  
    //Method to print memory
    public static void printMemory(){
        System.out.println("Addr\t0\t1\t2\t3\t4\t5\t6\t7\t8\t9\tA\tB\tC\tD\tE\tF");
        
        String row = "4020";
        for(int i=0; i<memory.length; i++){
            for(int j=0; j<16; j++){
                if(j==0){
                    System.out.print(row + "\t");
                    row = String.format("%04X", Integer.parseInt(row, 16) + 16);
                }    
                if(memory[i][j] == null)
                    memory[i][j]= "--";
                System.out.print(memory[i][j] + "\t");
            }
            System.out.println();
        }
    }
}
