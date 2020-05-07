
/**
 * @author rsninja, William Meathrel
 * Mastermind game for ics class
 * May 7, 2020
 */

import java.util.ArrayList;
import java.util.List;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ServerHandler extends SimpleChannelInboundHandler<String> {

    // List of connected client channels.
    static final List<Channel> channels = new ArrayList<Channel>();

    // amount of players currently in the game
    static int playerCount = 0;

    // channel position of the code breaker
    static int codeBreaker = 0;

    // channel position of the code maker
    static int codeMaker = 1;

    // state the server is in
    static ServerState serverState = ServerState.AcceptPlayers;

    // Stores the code, codes consist of 4 chars: 1 2 3 4 5 6 in any combination
    static String code;

    // Stores the guess same as code
    static String codeGuess;

    // server states
    enum ServerState {
        AcceptPlayers, // [AP]
        WaitForCode, // [WC]
        WaitForGuess, // [WG]
        WaitForAcknowledgement // [WA]
    }
    // code prefix == C
    // guess prefix == G

    // Messages all clients
    public void messageAllClient(String msg) {
        for (Channel c : channels) {
            c.writeAndFlush(msg);
        }
    }

    // Messages code breaker
    public void messageCodeBreaker(String msg) {
        channels.get(codeBreaker).writeAndFlush(msg);
    }

    // Messages code maker
    public void messageCodeMaker(String msg) {
        channels.get(codeMaker).writeAndFlush(msg);
    }

    // is player zero
    public boolean isPlayerZero(ChannelHandlerContext ctx) {
        if (ctx.channel().equals(channels.get(0))) {
            return true;
        }
        return false;
    }

    // is maker
    public boolean isMaker(ChannelHandlerContext ctx) {
        if (ctx.channel().equals(channels.get(codeMaker))) {
            return true;
        }
        return false;
    }

    // What happens when player connects
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        System.out.println("Client joined - " + ctx);
        playerCount++;
        channels.add(ctx.channel());
        ctx.writeAndFlush("[AP] Successfully Joined");
    }

    // what happens when a client sends a message
    @Override
    public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        msg = msg.substring(msg.indexOf(":") + 2);
        System.out.println("recived: " + msg);
        switch (serverState) {
            case AcceptPlayers:
                handleAcceptPlayers(ctx, msg);
                break;
            case WaitForCode:
                handleWaitForCode(ctx, msg);
                break;
            case WaitForGuess:
                handleWaitForGuess(ctx, msg);
                break;
            case WaitForAcknowledgement:
                handleWaitForAcknowledgement(ctx, msg);
                break;
        }
    }

    /*
     * In case of exception, close channel. One may chose to custom handle exception
     * & have alternative logical flows.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("Closing connection for client - " + ctx);
        playerCount--;
        ctx.close();
    }

    // swaps player roles
    public void swapPlayers() {
        int oldCodeBreaker = codeBreaker;
        codeBreaker = codeMaker;
        codeMaker = oldCodeBreaker;
    }

    public static boolean readyPlayerZero = false;
    public static boolean readyPlayerOne = false;

    // handles accepting players state
    public void handleAcceptPlayers(ChannelHandlerContext ctx, String msg) {

        // checks if player zero is ready to play
        if (msg.equals("[AP]ready") && isPlayerZero(ctx)) {
            readyPlayerZero = true;
            messageAllClient("[AP]Player Zero Ready");
        }

        // checks if player one is ready to play
        if (msg.equals("[AP]ready") && !isPlayerZero(ctx)) {
            readyPlayerOne = true;
            messageAllClient("[AP]Player One Ready");
        }

        // if both players are ready start the game.
        if (readyPlayerOne && readyPlayerZero) {
            serverState = ServerState.WaitForCode;
            swapPlayers();
            messageAllClient("[AP]Game Starting \n");
            messageCodeMaker("[WC]SendCodePlease");
            messageCodeBreaker("[WC]YouAreCodeBreaker");
        }
      
    }

    // handles waiting for code state
    public void handleWaitForCode(ChannelHandlerContext ctx, String msg) {
        System.out.println("got a guess");
        if(isMaker(ctx) && msg.charAt(0) == 'C'){
            System.out.println("Char code is C");
            code = msg.substring(1);
            System.out.println("code: " + code);
            if(verifyCode(code)) {
                System.out.println("Code has been varified");
                messageAllClient("[WC]CodeHasBeenSelected");
                messageCodeBreaker("[WC]SendGuessPlease");
                serverState = ServerState.WaitForGuess;
            }
        }

    }

    // makes sure a code is valid
    public boolean verifyCode(String code) {
        if(code.length() != 4) {
            System.out.println("Code not equal to 4");
            return false;
        }

        for(int i = 0;i<4;i++) {
            char c = code.charAt(i);
            if(c != '1' && c != '2' && c != '3' && c != '4' && c != '5' && c != '6') {
                System.out.println("Code not just 123456");
                return false;
            }
        }

        return true;
    }

    // handles waiting for guess state
    public void handleWaitForGuess(ChannelHandlerContext ctx, String msg) {
        if(!isMaker(ctx) && msg.charAt(0) == 'G'){
            codeGuess = msg.substring(1);
            if(verifyCode(codeGuess)) {
                messageAllClient("[WG]GuessReceived");
                messageCodeMaker("C"+codeGuess);
                messageCodeMaker("[WG]SendAcknowledgementPlease");
                serverState = ServerState.WaitForAcknowledgement;
            }
        }
    }

    // hint is 4 character long code consisting of 0s, 1s, and 2s
    public String generateHint(String guess){
        StringBuilder returnStr = new StringBuilder("G");

        ArrayList<Integer> correctGuesses = new ArrayList<Integer>();

        for(int i = 0; i < 4; i++){
            if(guess.charAt(i) == code.charAt(i)) {
                returnStr.append("2");
                correctGuesses.add(i);
            }
        }

        for(int i = 0; i < 4; i++) {
            if(correctGuesses.contains(i)) {
                continue;
            }

            for(int j=0;j<4;j++) {
                if(correctGuesses.contains(j)) {
                    continue;
                }

                if(guess.charAt(i) == code.charAt(j)) {
                    returnStr.append("1");
                }
            }
        }

        while(returnStr.length() < 5) {
            returnStr.append("0");
        }

        return returnStr.toString();
    }


    // handles waiting for acknowledgement
    public void handleWaitForAcknowledgement(ChannelHandlerContext ctx, String msg) {
        if(isMaker(ctx) && msg.equals("[WA]acknowledgement")) {
            messageAllClient(generateHint(codeGuess));
            messageAllClient("[WA]WaitingForGuess \n");
            messageCodeBreaker("[WA]SendGuessPlease \n");
            serverState = ServerState.WaitForGuess;
        }
    }
}