import kotlinx.coroutines.*
import java.awt.Color
import java.awt.Dimension
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter

class MainFrame : JFrame(){
    private val parser: Parser

    private val basePanel: JPanel
    private val inputArea: JTextArea
    private val outputArea: JTextArea
    private val splitPane: JSplitPane

    private var fileChooser: JFileChooser

    private val pathTxtFld: JTextField
    private val orLbl: JLabel
    private val chooseBtn: JButton
    private val openBtn: JButton

    private val findAllNPLbl: JLabel
    private val findBtn: JButton
    private val numOfSentencesLbl: JLabel
    private val countLbl: JLabel

    private var isOutputEditable: JCheckBoxMenuItem
    private var isDynamicProcessing: JCheckBoxMenuItem
    private var isAsyncProcessing: JCheckBoxMenuItem
    private var parseModel: JMenuItem
    private var outputLineWrap: JCheckBoxMenuItem
    private var inputLineWrap: JCheckBoxMenuItem

    private val h: Highlighter

    private var thr: Thread? = null
    private var job: Job? = null

    init{
        setSize(1000, 700)
        minimumSize = Dimension(450, 500)
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

        parser = Parser()

        basePanel = JPanel(true)
        inputArea = JTextArea()
        outputArea = JTextArea()
        splitPane = JSplitPane()

        fileChooser = JFileChooser()

        pathTxtFld = JTextField("C:\\Users\\yansu\\Documents\\sentences.txt")
        orLbl = JLabel("or")
        chooseBtn = JButton("Choose")
        openBtn = JButton("Open")

        findAllNPLbl = JLabel("Find all noun phrases", JLabel.LEFT)
        findBtn = JButton("Find")
        numOfSentencesLbl = JLabel("Number of sentences")
        countLbl = JLabel("0")

        h = inputArea.highlighter

        setUpSplitPane()

        val gl = GroupLayout(this.contentPane)
        layout = gl

        createLayout(gl)

        isAsyncProcessing = JCheckBoxMenuItem("Async processing", true)
        isDynamicProcessing = JCheckBoxMenuItem("Dynamic processing", true)
        parseModel = JMenuItem("Set Parse Model")
        isOutputEditable = JCheckBoxMenuItem("Editable", false)
        inputLineWrap = JCheckBoxMenuItem("Line wrap", true)
        outputLineWrap = JCheckBoxMenuItem("Line wrap", true)
        jMenuBar = createMenuBar()

        isVisible = true

        inputArea.wrapStyleWord = true
        outputArea.wrapStyleWord = true

        inputArea.lineWrap = inputLineWrap.state
        outputArea.lineWrap = outputLineWrap.state
        outputArea.isEditable = isOutputEditable.state


        //region Menu Bar Listeners
        isOutputEditable.addActionListener { outputArea.isEditable = isOutputEditable.state }

        inputLineWrap.addItemListener { inputArea.lineWrap = inputLineWrap.isSelected }
        outputLineWrap.addItemListener { outputArea.lineWrap = outputLineWrap.isSelected }

        parseModel.addActionListener {
            val ss = fileChooser.showOpenDialog(this)
            if (ss == JFileChooser.APPROVE_OPTION) {
                parser.parserModel = fileChooser.selectedFile.absolutePath
            }
        }
        //endregion Menu Bar Listeners

        //region Button Listeners
        chooseBtn.addActionListener {
            val ss = fileChooser.showOpenDialog(this)
            if (ss == JFileChooser.APPROVE_OPTION) {
                pathTxtFld.text = fileChooser.selectedFile.absolutePath
            }
        }

        openBtn.addActionListener { setTextFromFile(pathTxtFld.text) }

        findBtn.addActionListener {
            parser.clearCollections()
            findSentences()
        }
        //endregion Button Listeners

        inputArea.addCaretListener { e ->
            highlightSentence(e.dot)
        }
    }

    private fun highlightSentence(dot: Int) {
        h.removeAllHighlights()

        val pattern = Pattern.compile(
            "[\"']?[A-Z][^.?!]+((?![.?!]['\"]?\\s[\"']?[A-Z][^.?!]).)+[.?!'\"]+",
            Pattern.MULTILINE or Pattern.UNICODE_CASE
        )

        val matcher = pattern.matcher(inputArea.text)

        val hp = DefaultHighlighter.DefaultHighlightPainter(Color(0f, 1f, 0f, 0.3f))

        while (matcher.find()) {
            if (dot >= matcher.start() && dot <= matcher.end()) {
                try {
                    h.addHighlight(matcher.start(), matcher.end(), hp)
                } catch (e1: BadLocationException) {
                    e1.printStackTrace()
                }

                if (isDynamicProcessing.state) {
                    if (isAsyncProcessing.state)
                        parser.findNounPhrasesAsync(
                            inputArea.text.substring(
                                matcher.start(),
                                matcher.end()
                            ),
                            outputArea
                        )
                    else
                        outputArea.text = parser.findNounPhrases(
                            inputArea.text.substring(
                                matcher.start(),
                                matcher.end()
                            )
                        )
                }
            }
        }
    }

    private fun findSentences() {
        h.removeAllHighlights()

        val pattern = Pattern.compile(
            "[\"']?[A-Z][^.?!]+((?![.?!]['\"]?\\s[\"']?[A-Z][^.?!]).)+[.?!'\"]+",
            Pattern.MULTILINE or Pattern.UNICODE_CASE
        )

        val matcher = pattern.matcher(inputArea.text)

        val hp = DefaultHighlighter.DefaultHighlightPainter(Color(0f, 1f, 1f, 0.3f))

        outputArea.text = ""

        var count = 0
        val sentences = mutableListOf<String>()

        while (matcher.find()) {
            count++
            this.countLbl.text = count.toString()
            try {
                h.addHighlight(matcher.start(), matcher.end(), hp)
            } catch (e1: BadLocationException) {
                e1.printStackTrace()
            }
            sentences.add(inputArea.text.substring(matcher.start(), matcher.end()))
            if (isAsyncProcessing.state)

            else
                outputArea.append(
                    parser.findNounPhrases(
                        inputArea.text.substring(matcher.start(), matcher.end())
                    ) + "\n"
                )

            if (matcher.hitEnd())
                parser.findAllNounPhrasesAsync(sentences, outputArea)
        }
    }


    private fun setTextFromFile(path: String?) = runBlocking{
        if(job != null){
            println(job?.cancelAndJoin())
        }

        inputArea.text = ""

        val file = File(path)
        if (file.exists() && file.isFile) {
            var str = ""
            job = GlobalScope.launch {
                inputArea.text = ":::OPENING FILE, PLEASE WAIT"
                val tmpAsync = this.async {
                    var buffer = ""
                    try {
                        file.forEachLine {
                            if (isActive)
                                buffer += it + "\n"
                        }
                    } catch (ex: IOException) {
                        println(ex.message)
                    }
                    return@async buffer
                }
                str = tmpAsync.await()
            }
            job?.invokeOnCompletion { inputArea.text = str }
        }
    }

    private fun createMenuBar(): JMenuBar? {
        val menuBar = JMenuBar()
        val settings = JMenu("Settings")
        val jmInputArea = JMenu("Input Area")
        val jmOutputArea = JMenu("Output Area")
        val processing = JMenu("Processing")

        jmInputArea.add(outputLineWrap)
        jmOutputArea.add(isOutputEditable)
        jmOutputArea.add(inputLineWrap)
        processing.add(isAsyncProcessing)
        processing.add(isDynamicProcessing)
        settings.add(parseModel)
        settings.add(processing)
        settings.add(jmOutputArea)
        settings.add(jmInputArea)
        menuBar.add(settings)

        return menuBar
    }

    private fun setUpSplitPane(){
        splitPane.orientation = JSplitPane.VERTICAL_SPLIT
        splitPane.topComponent = JScrollPane(inputArea)
        splitPane.bottomComponent = JScrollPane(outputArea)
        splitPane.resizeWeight = 0.5
    }

    private fun createLayout(gl: GroupLayout) {
        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGap(6)
                .addGroup(
                    gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(pathTxtFld, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(orLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(chooseBtn,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(openBtn,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                )
                .addGap(6)
                .addComponent(splitPane)
                .addGap(6)
                .addGroup(
                    gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(findAllNPLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(findBtn,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(numOfSentencesLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(countLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                )
                .addGap(6)
        )

        gl.setHorizontalGroup(
            gl.createSequentialGroup()
                .addGap(6)
                .addGroup(
                    gl.createParallelGroup()
                        .addGroup(
                            gl.createSequentialGroup()
                                .addComponent(pathTxtFld,GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE)
                                .addGap(10)
                                .addComponent(orLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(10)
                                .addComponent(chooseBtn,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(10)
                                .addComponent(openBtn,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        )
                        .addGap(6)
                        .addComponent(splitPane)
                        .addGap(6)
                        .addGroup(
                            gl.createSequentialGroup()
                                .addComponent(findAllNPLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(10)
                                .addComponent(findBtn,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(50)
                                .addComponent(numOfSentencesLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(10)
                                .addComponent(countLbl,GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        )
                )
                .addGap(6)
        )
    }
}