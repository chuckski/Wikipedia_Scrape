/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package wikipedia.scrape;

import java.io.BufferedReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import sun.misc.SignalHandler;
import sun.misc.Signal;

/**
 * 
 * @author Charles Pagano
 *
 */
public class WikipediaScrape {

    /**
     * getTopicFromCmdLine - A function that will handle our command-line arguments, 
     * if given.  Code is broken out into this function from Main for clarity.  
     * Command-line arguments can be passed in two ways:  
     * a named argument called topic:
     *      /topic:[topic name] or /topic [topic name],
     * or just including the topic name on the command line:
     *      java -jar WikipediaScrape.jar [topic name]
     * 
     * @param args The command line arguments from the main function
     * @returns A String containing the properly-formatted topic name, if found.
     * If no topic is found, an empty String ("") is returned.
     */
    private static String getTopicFromCmdLine(String[] args) {
        // If there are no elements in the args array, return an empty string
        try {
            if (args.length == 0) {
                return("");
            }
            // If an empty arg is encountered, return an empty string.
            for (String a: args) {
                if (a.equals("")) {
                    return("");
                }
            }
        }
        catch (Exception e) {
            // NullPointerException would also be caught here (Ctrl-C)
            return("");
        }
        
        
        // There are elements in the args array, so hold the topic, in case one 
        // is found
        String topic = "";
        
        // Get the command-line args into a list, and see if the first element
        // contains our argument name
        List<String> arglist = Arrays.asList(args);
        
        String first = arglist.get(0);
        
        // See if the first element in the list matches a regex, which
        // is geared at identifying the topic command line argument whose
        // value starts in the next element in the list.  If this is a match,
        // the first part of 'topic' is in the group at index 3 (index 0 is 
        // the entire matched string).
        Pattern embedded = Pattern.compile("^(\\-|\\-\\-|\\/)topic(\\:|\\=)([\\w\\s]+)$");
        Matcher embeddedMatcher = embedded.matcher(first);
        
        Pattern regular = Pattern.compile("^(\\-|\\-\\-|\\/)topic$");
        Matcher regularMatcher = regular.matcher(first);
        
        // Hold a flag that tells us if we need to skip the first
        // element in the list or not, when processing more than one element.
        boolean skipFirstElement = false;
        
        if (embeddedMatcher.matches()) {
            // Found the beginning of the topic
            if (embeddedMatcher.group(3) != null) {
                // Save the beginning of the topic
                topic = embeddedMatcher.group(3);

                // Skip this element from the list for further processing
                // But only do so if there's more than one element in the list!
                if (arglist.size() == 1) {
                    // this is the only element in the lst, so replace topic's
                    // spaces with underscores and return it
                    return(topic.replace(" ","_"));
                }
                // There is more than one element in the list, so you can safely 
                // skip the first
                skipFirstElement = true;
            }
            else {
                // On error getting the value portion of this, return an empty
                // string.
                return("");
            }
        }
        else if (regularMatcher.matches()) {
            // The first element in arglist signals the beginning of our 
            // topic, but there is no value embedded in the first element.
            // Skip it, but only if there are more elements.
            if (arglist.size() == 1) {
                // Although the non-embedded value form was used, a value
                // was not provided.  Return an empty string.
                return("");
            }
            
            // There are more elements in the list, so you can safely skip 
            // the first.
            skipFirstElement = true;
        }
        
        // If processing has gotten here, then either a valid argument name 
        // for 'topic' has been provided, or none was found.  If there are 
        // any elements left in arglist, assume they are fragments of the 
        // desired topic.  Assemble them into a string, and then replace
        // all spaces in that string with underscores.
        
        // keep an index so you know which element is being examined
        int idx = 0;
        for (String a: arglist) {
            if (skipFirstElement && idx == 0) {
                // Increment the index so this doesn't end up skipping all elements
                idx += 1;
                continue;
            }
            
            // Assume that if the topic variable is not empty, a space
            // needs to be prepended to 'a' before pushing it on to the 
            // end of the string
            topic = String.format("%s%s%s", 
                                  topic,
                                  (topic.equals("")) ? "" : " ",
                                  a);
        }
        
        // Replace all spaces in topic with underscores, then return it.
        topic = topic.replace(" ", "_");
        
        return topic;
    } 
    
    /**
     * promptUserForTopic - Prompt the user via command line to enter a topic.
     * The user can break with the Ctrl-C key sequence or by entering a 
     * topic.
     * @return String - topic in the proper format for Wikipedia (all spaces
     * replaced with underscores)
     */
    private static String promptUserForTopic() {
        // Store the topic
        String topic = "";
        
        // Make sure there is a console
        Console c = System.console();
        
        // Begin prompting the user for a topic.
        // Now, if we do have a topic, let's go try to find it.
        // If not, then prompt the user for one.  They must enter a topic
        // or Ctrl-C to quit.
        while (true) {
            // Prompt for a topic. replace spaces with underscores.
            String input = c.readLine("Please provide a Wikipedia topic (or Ctrl-C to quit): ");

            if (input.equals("\n")) {
                // no real topic give, ask again
                continue;
            }
            
            // The input was not just a newline, so use it
            topic = input;
            
            if (!topic.equals("")) {
                // A topic has been provided. swap spaces for underscores
                topic = topic.replace(" ", "_");
                
                // Break the loop, no further processing is needed
                break;
            }
        }
        
        return topic;
    }
    
    /**
     * fetchTopic - Fetch a topic from https://en.wikipedia.org, if it exists,
     * and print its introductory paragraph to the console.
     * @param topic The topic to search for and display, if it exists.     
     */
    private static void fetchTopic(String topic) {
        // signal handling - handle SIGINT
        Signal.handle(new Signal("INT"), new SignalHandler() {
            public void handle(Signal sig) {
                // print a notice indicating that the user terminated
                System.out.printf("\nUser terminated the application.\n");
                System.exit(0);
            }
        });
     
        String sBaseURL = String.format("https://en.wikipedia.org/wiki/%s", topic);
        
        try {
            /*
             * Use an HttpURLConnection object to send a GET to wikipedia.
             * Examine the reponse code and check for 200.  If that's not 
             * what is returned, react accordingly.  If a 200 OK is returned,
             * save the response text in its entirety, and use Jsoup to parse it
             * into a DOM document.
             */
            URL url = new URL(sBaseURL);
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(30*1000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            conn.setRequestMethod("GET");
            conn.connect();
            
            // Check the status of the request
            int iStatusCode = conn.getResponseCode();
            
            // Read the contents of the page if the status is 200
            switch(iStatusCode) {
                case 200:
                    // break from this switch so that processing continues
                    break;
                case 404:
                    // not found, inform the user and terminate per spec.
                    System.out.printf("Not found.\n");
                    System.exit(1);
                    break;
                default:
                    // Some other non-200 status was returned.
                    // Notify of error and terminate.
                    System.out.printf("Error processing request, status=%d\n", iStatusCode);
                    System.exit(1);
                    break;
            }
            
            // Continue fetching the page contents for parsing with Jsoup
            BufferedReader reader = 
                    new BufferedReader(new InputStreamReader(conn.getInputStream()));
            
            StringBuilder sb = new StringBuilder();
            
            String line = "";
            while ((line = reader.readLine()) != null) {
                sb.append(String.format("%s\n", line));
            }
            reader.close();
            
            // Parse the document from text
            Document doc = Jsoup.parse(sb.toString());
            
            // Get the first paragraph, and then clean its text.
            // It's not that we don't trust Wikipedia, it's that 
            // we don't trust anybody.
            Element ppg = doc.select("p").first();
            String ppgText = Jsoup.clean(ppg.text(), Whitelist.basic());
            
            // Now we can print the text to the console.  The spec does not
            // state whether or not the topic name should be printed, so it's
            // not printed.  Start with a newline so that it's a litle easier
            // to read.
            System.out.printf("\n%s\n", ppgText);
            
            // On success, return control to the main function
            return;
        }
        catch (java.net.MalformedURLException me) {
            System.out.printf("Malformed URL Exception: %s\n", me.toString());
        }
        catch (java.net.ConnectException ce) {
            System.out.printf("Connection Exception: %s\n", ce.toString());
        }
        catch (IOException io) {
            System.out.printf("IO Exception: %s\n", io.toString());
        }
        
        // If an exception was encountered, processing will resume here.
        // Exit 1 to indicate an error.
        System.exit(1);
    }
    
    /**
     * @param args the command line arguments
     * @throws InterruptedException on Ctrl-C
     * 
     * Parameters that are accepted are as follows:
     *  /topic   or   -topic  or --topic  followed by the desired Wikipedia topic
     * 
     *  as above, except that topic is followed immediately by : or =, which
     *  is then immediately followed by the desired Wikipedia topic.  Example:
     *      java -jar WikipediaScrape.jar /topic:Babe Ruth
     *      java -jar WikipediaScrape.jar /topic=Babe Ruth
     *      java -jar WikipediaScrape.jar /topic Babe Ruth
     *  All three examples will load the Wikipedia page for Babe Ruth.
     * 
     *  the desired Wikipedia topic, with no other arguments:
     *      java -jar WikipediaScrape.jar Babe Ruth
     *  The above will also load Babe Ruth's Wikipedia page
     * 
     *  To display usage, provide any of these as the first argument:
     *  -h, /h, --h, -help, /help, --help, /?, -?, --?
     * 
     *  A usage message will print, and then the program will terminate.
     */
    public static void main(String[] args) throws InterruptedException {
        // Make sure there is a console before proceeding
        Console c = System.console();
        if (c == null) {
            System.err.println("Error - must have a console!");
            System.exit(1);
        }

        String helpMessage = 
"\n\nWikipediaScrape - find a topic on Wikipedia's EN site, and print its\n" +
"introductory paragraph.\n\n" +
"Usage:\n\n" +
"java -jar WikipediaScrape.jar [/topic | -topic | --topic] topicName\n" +
"    where topicName is the name of a Wikipedia topic\n\n" +
"java -jar WikipediaScrape.jar [/topic | -topic | --topic](= | :)topicName\n" +
"    where topicName is the name of a Wikipedia topic. Examples:\n" +
"        java -jar WikipediaScrape.jar /topic:Babe Ruth or\n" +
"        java -jar WikipediaScrape.jar --topic=Babe Ruth\n\n" +
"java -jar WikipediaScrape.jar topicName\n" +
"    where topicName is the name of a Wikipedia topic.\n\n" +
"java -jar WikipediaScrape.jar [-? | --? | /? | -h[elp] | --h[elp] | /h[elp]\n" +
"The topic name does not need to be enclosed in quotes if there are spaces,\n" +
"this program will account for that (quotes are OK though).\n\n" +
"If an existing Wikipedia topic is provided,\n" +
"the introductory paragraph will be displayed.  If the topic is not found, a\n" +
"message indicating that will be displayed.  If no topic name is provided,\n" +
"you will be prompted for one.  The program can be exited at any time by\n" +
"pressing Ctrl-C on the keyboard.";
        
        // Check to see if the user asked for help.  Don't display unless
        // there is a help argument, because calling this program with no
        // arguments is supposed to lead to the user being prompted for a target.
        if (args.length > 0) {
            Pattern p = Pattern.compile("^(\\-|\\-\\-|\\/)(\\?|h(?:(elp|)))$");
            Matcher m = p.matcher(args[0]);
            
            if (m.matches()) {
                // The user asked for help. Display help and exit.
                System.out.printf("%s\n", helpMessage);
                System.exit(0);
            }
        }
        
        // signal handling - handle SIGINT
        Signal.handle(new Signal("INT"), new SignalHandler() {
            public void handle(Signal sig) {
                // print a notice indicating that the user terminated
                System.out.printf("\nUser terminated the application.\n");
                System.exit(0);
            }
        });
        
        // Try to find the topic from command line arguments
        // before prompting the user.
        String topic = getTopicFromCmdLine(args);
        
        // If no topic was provided on the command line, the user must
        // be prompted for one.
        if (topic.equals("")) {
            try {
                topic = promptUserForTopic();
            }
            // catch the NullPointerException that will be fired
            // if the user presses Ctrl-C, and just exit.
            catch (NullPointerException npe) {
                System.exit(1);
            }
            catch (Exception e) {
                // The spec does not define behavior in this case, 
                // so exit after printing a message to STDOUT
                System.out.printf(
                        "An exception ocurred when trying to prompt " +
                        "the user for a topic: %s\n", 
                        e.toString());
                System.exit(1);
            }
        }
        
        /*
         * The above should have yielded a topic.  Navigate to the topic's page,
         * if it exists.  We'll know it doesn't exist if the response code 
         * is not 200 OK, but the fetch function will handle all of that.
         */
        fetchTopic(topic);
        
        // If the fetchTopic call did not exit with an error code, 
        // exit here indicating success.
        System.exit(0);
    }
}