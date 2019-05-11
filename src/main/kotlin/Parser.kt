import edu.stanford.nlp.parser.lexparser.LexicalizedParser
import edu.stanford.nlp.process.CoreLabelTokenFactory
import edu.stanford.nlp.process.PTBTokenizer
import edu.stanford.nlp.trees.Tree
import kotlinx.coroutines.*

import java.io.StringReader
import java.util.*
import javax.swing.JTextArea

internal class Parser {
    var parserModel: String = "C:\\Users\\yansu\\IdeaProjects\\StanfordParser\\src\\englishPCFG.ser.gz"
    private val lp: LexicalizedParser
    private var count: Int = 0
    private var cor: Job? = null
    private var job2async: Job? = null

    init {
        lp = LexicalizedParser.loadModel(parserModel)
    }

    fun findNounPhrasesAsync(sentence: String, component: JTextArea) = runBlocking{
        if(job2async != null){
            job2async?.cancelAndJoin()
        }

        component.text = ":::PROCESSING, PLEASE WAIT"
        var str = ""

        job2async = GlobalScope.launch {
            val job = this.async { createReturnList(sentence) }
            str = job.await()
        }

        job2async?.invokeOnCompletion { component.text = str }
    }

    fun findAllNounPhrasesAsync(sentences: List<String>, component: JTextArea) = runBlocking{
        if(cor != null){
            cor?.cancelAndJoin()
        }
        cor = doALotOfCoroutines(sentences, component)
    }

    private fun doALotOfCoroutines(sentences: List<String>, component: JTextArea) = GlobalScope.launch {
        component.text = ":::PROCESSING, PLEASE WAIT"

        val coroutines = mutableListOf<Deferred<String>>()
        for (i in 0 until sentences.size) {
            coroutines.add(this.async { createReturnList(sentences[i]) })
        }
        var check = true
        for (j in coroutines) {
            if(check){
                component.text = ""
                check = false
            }
            component.append(j.await() + "\n")
        }
    }

    private fun createReturnList(sentence: String) : String{
        val c = ++count
        println("start coroutine $c")
        var ret = ""

        val parse = createTree(sentence)

        for (subTree in parse) {
            if (subTree.label().value() == "NP") {
                ret += editOutputTree(subTree) + "\n"
            }
        }

        println("end coroutine $c")
        return ret
    }

    fun findNounPhrases(sentence: String) : String {
        val parse = createTree(sentence)

        var ret = ""
        for (subTree in parse) {
            if (subTree.label().value() == "NP") {
                ret += editOutputTree(subTree) + "\n"
            }
        }

        return ret
    }

    private fun editOutputTree(tree: Tree): String {
        val leaves = tree.getLeaves<Tree>().toTypedArray()

        var print = Arrays.toString(leaves)
        print = print.replace(",", "")
        print = print.replace("[", "")
        print = print.replace("]", "")
        print = print.replace(" {2,}".toRegex(), " ")
        return print
    }

    private fun createTree(sentence: String): Tree{
        val tokenizerFactory = PTBTokenizer.factory(CoreLabelTokenFactory(), "")
        val tok = tokenizerFactory.getTokenizer(StringReader(sentence))
        val rawWords2 = tok.tokenize()
        return lp.apply(rawWords2)
    }

    fun clearCollections(){
        count = 0
    }
}
