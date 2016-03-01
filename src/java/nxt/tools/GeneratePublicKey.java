/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.tools;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Logger;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

public class GeneratePublicKey {

    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("Usage: java nxt.tools.GeneratePublicKey");
            System.exit(1);
        }
        Logger.setLevel(Logger.Level.ERROR);
        String secretPhrase;
        Console console = System.console();
        if (console == null) {
            try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in))) {
                while ((secretPhrase = inputReader.readLine()) != null) {
                    byte[] publicKey = Crypto.getPublicKey(secretPhrase);
                    System.out.println(Convert.toHexString(publicKey));
                }
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        } else {
            char[] chars;
            while ((chars = console.readPassword("Enter secret phrase: ")) != null && chars.length > 0) {
                secretPhrase = new String(chars);
                byte[] publicKey = Crypto.getPublicKey(secretPhrase);
                System.out.println(Convert.toHexString(publicKey));
            }
        }
    }
}
