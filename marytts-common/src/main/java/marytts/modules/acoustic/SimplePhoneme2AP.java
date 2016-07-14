/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.modules.acoustic;

import java.util.Locale;
import java.util.StringTokenizer;
import java.util.ArrayList;

import marytts.io.XMLSerializer;
import marytts.data.Utterance;
import marytts.data.Sequence;
import marytts.data.Relation;
import marytts.data.item.linguistic.*;
import marytts.data.item.prosody.*;
import marytts.data.item.phonology.*;
import marytts.data.utils.IntegerPair;
import marytts.data.utils.SequenceTypePair;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.modules.nlp.phonemiser.Allophone;
import marytts.modules.nlp.phonemiser.AllophoneSet;
import marytts.modules.synthesis.Voice;
import marytts.server.MaryProperties;
import marytts.util.MaryRuntimeUtils;
import marytts.util.MaryUtils;

import marytts.modules.InternalModule;


/**
 * Read a simple phone string and generate default acoustic parameters.
 *
 * @author Marc Schr&ouml;der
 */

public class SimplePhoneme2AP extends InternalModule {
	protected AllophoneSet allophoneSet;

    public SimplePhoneme2AP(String localeString) {
		this(MaryDataType.SIMPLEPHONEMES, MaryDataType.ACOUSTPARAMS, MaryUtils.string2locale(localeString));
	}

    public SimplePhoneme2AP(MaryDataType inputType, MaryDataType outputType, Locale locale) {
		super("SimplePhoneme2AP", inputType, outputType, locale);
    }

	public void startup() throws Exception {
		allophoneSet = MaryRuntimeUtils.needAllophoneSet(MaryProperties.localePrefix(getLocale()) + ".allophoneset");
		super.startup();
	}

	public MaryData process(MaryData d)
        throws Exception
    {
        String phoneString = d.getPlainText();
        Utterance utt = new Utterance(phoneString, d.getLocale());

        Accent word_accent = null;
        int cumulDur = 0;
		boolean isFirst = true;
        Sequence<Word> words = new Sequence<Word>();
        StringTokenizer stTokens = new StringTokenizer(phoneString);
		while (stTokens.hasMoreTokens())
        {
			String tokenPhonemes = stTokens.nextToken();
			StringTokenizer stSyllables = new StringTokenizer(tokenPhonemes, "-_");

            ArrayList<Syllable> syllables = new ArrayList<Syllable>();
			while (stSyllables.hasMoreTokens())
            {
                // Get syllable string
				String syllablePhonemes = stSyllables.nextToken();

                // Setting stress
				int stress = 0;
				if (syllablePhonemes.startsWith("'"))
					stress = 1;
				else if (syllablePhonemes.startsWith(","))
					stress = 2;


                // Simplified: Give a "pressure accent" do stressed syllables
                Accent syllable_accent = null;
                if (stress != 0) {
                    word_accent = new Accent("*");
                    syllable_accent = new Accent("*");
                }

                // Generate phone
                ArrayList<Phoneme> phones = new ArrayList<Phoneme>();
                Allophone[] allophones = allophoneSet.splitIntoAllophones(syllablePhonemes);
				for (int i = 0; i < allophones.length; i++) {
                    // Dealing with duration of the phone
					int dur = 70;
					if (allophones[i].isVowel()) {
						dur = 100;
						if (stress == 1)
							dur *= 1.5;
						else if (stress == 2)
							dur *= 1.2;
					}

                    // Creating the phone
                    Phone ph = new Phone(allophones[i].name(), cumulDur, dur);
                    phones.add(ph);

                    // We save the cumulative duration to know when the next phone is starting
                    cumulDur += dur;
                }

                Syllable syl = new Syllable(phones, stress);
                syl.setAccent(syllable_accent);
                syllables.add(syl);
            }

            // Wrapping into a word
            Word w = new Word("", syllables);
            w.setAccent(word_accent);
            words.add(w);
        }

        utt.addSequence(Utterance.SupportedSequenceType.WORD, words);

        // Wrapping into a phrase
        Boundary boundary = new Boundary(4, 400);
        Phrase phrase = new Phrase(boundary);
        Sequence<Phrase> phrases = new Sequence<Phrase>();
        phrases.add(phrase);
        utt.addSequence(Utterance.SupportedSequenceType.PHRASE, phrases);
        ArrayList<IntegerPair> alignment_phrase_word = new ArrayList<IntegerPair>();
        for (int i=0; i<words.size(); i++)
            alignment_phrase_word.add(new IntegerPair(0, i));
        utt.setRelation(Utterance.SupportedSequenceType.PHRASE,
                        Utterance.SupportedSequenceType.WORD,
                        new Relation(phrases, words, alignment_phrase_word));

        // Wrapping into a sentence
        Sentence sentence = new Sentence("");
        Sequence<Sentence> sentences = new Sequence<Sentence>();
        sentences.add(sentence);
        utt.addSequence(Utterance.SupportedSequenceType.SENTENCE, sentences);
        ArrayList<IntegerPair> alignment_sentence_phrase = new ArrayList<IntegerPair>();
        alignment_sentence_phrase.add(new IntegerPair(0, 0));
        utt.setRelation(Utterance.SupportedSequenceType.SENTENCE,
                        Utterance.SupportedSequenceType.PHRASE,
                        new Relation(sentences, phrases, alignment_sentence_phrase));

        // Wrapping into a paragraph
        Paragraph paragraph = new Paragraph("");
        Sequence<Paragraph> paragraphs = new Sequence<Paragraph>();
        paragraphs.add(paragraph);
        utt.addSequence(Utterance.SupportedSequenceType.PARAGRAPH, paragraphs);
        ArrayList<IntegerPair> alignment_paragraph_sentence = new ArrayList<IntegerPair>();
        alignment_paragraph_sentence.add(new IntegerPair(0, 0));
        utt.setRelation(Utterance.SupportedSequenceType.PARAGRAPH,
                        Utterance.SupportedSequenceType.SENTENCE,
                        new Relation(paragraphs, sentences, alignment_paragraph_sentence));


        // Finally serialize and return
        XMLSerializer xml_ser = new XMLSerializer();
        MaryData result = new MaryData(outputType(), d.getLocale());
        result.setDocument(xml_ser.generateDocument(utt));
        return result;
    }
}
