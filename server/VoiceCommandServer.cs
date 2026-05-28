using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Windows.Forms;

namespace GupTaServer
{
    public class ServerGUI : Form
    {
        private Button     btnStart, btnStop, btnSetup;
        private Label      lblTitle, lblStatus, lblIp, lblNote;
        private PictureBox picQr;
        private Label      lblQrPlaceholder;

        private Process procWhisper, procBridge, procWake;
        private volatile bool _waitCancelled = false;
        private string appDir;
        private string pythonExe;
        private string whisperDir;

        public ServerGUI()
        {
            appDir    = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            pythonExe = Path.Combine(appDir, "python", "python.exe");
            if (!File.Exists(pythonExe)) pythonExe = "python";
            whisperDir = Path.Combine(appDir, "whisper");   // Vulkan whisper.cpp server + model
            InitializeComponent();
        }

        private void InitializeComponent()
        {
            this.Text            = "GupTa Server";
            this.Size            = new Size(460, 540);
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox     = false;
            this.StartPosition   = FormStartPosition.CenterScreen;
            this.BackColor       = Color.FromArgb(250, 250, 252);

            // Try to load icon
            string icoPath = Path.Combine(appDir, "gupta.ico");
            if (File.Exists(icoPath)) { try { this.Icon = new Icon(icoPath); } catch { } }

            lblTitle = new Label();
            lblTitle.Text      = "GupTa Server";
            lblTitle.Font      = new Font("Segoe UI", 20, FontStyle.Bold);
            lblTitle.ForeColor = Color.FromArgb(98, 0, 238);
            lblTitle.Bounds    = new Rectangle(10, 14, 430, 44);
            lblTitle.TextAlign = ContentAlignment.MiddleCenter;

            lblStatus = new Label();
            lblStatus.Text      = "● Server stopped";
            lblStatus.Font      = new Font("Segoe UI", 11);
            lblStatus.ForeColor = Color.Gray;
            lblStatus.Bounds    = new Rectangle(10, 62, 430, 26);
            lblStatus.TextAlign = ContentAlignment.MiddleCenter;

            lblIp = new Label();
            lblIp.Text      = "Laptop IP: " + GetLocalIp();
            lblIp.Font      = new Font("Segoe UI", 9);
            lblIp.ForeColor = Color.DimGray;
            lblIp.Bounds    = new Rectangle(10, 90, 430, 20);
            lblIp.TextAlign = ContentAlignment.MiddleCenter;

            picQr = new PictureBox();
            picQr.Bounds      = new Rectangle(115, 120, 220, 220);
            picQr.BackColor   = Color.White;
            picQr.BorderStyle = BorderStyle.FixedSingle;
            picQr.SizeMode    = PictureBoxSizeMode.Zoom;

            lblQrPlaceholder = new Label();
            lblQrPlaceholder.Text      = "QR code appears\nafter server starts";
            lblQrPlaceholder.Font      = new Font("Segoe UI", 9);
            lblQrPlaceholder.ForeColor = Color.LightGray;
            lblQrPlaceholder.TextAlign = ContentAlignment.MiddleCenter;
            lblQrPlaceholder.Dock      = DockStyle.Fill;
            picQr.Controls.Add(lblQrPlaceholder);

            Label lblQrHint = new Label();
            lblQrHint.Text      = "Scan in GupTa app → Settings ⚙ → QR icon";
            lblQrHint.Font      = new Font("Segoe UI", 8);
            lblQrHint.ForeColor = Color.DimGray;
            lblQrHint.Bounds    = new Rectangle(10, 344, 430, 18);
            lblQrHint.TextAlign = ContentAlignment.MiddleCenter;

            btnStart = new Button();
            btnStart.Text                     = "►  Start Server";
            btnStart.Font                     = new Font("Segoe UI", 11, FontStyle.Bold);
            btnStart.BackColor                = Color.FromArgb(98, 0, 238);
            btnStart.ForeColor                = Color.White;
            btnStart.FlatStyle                = FlatStyle.Flat;
            btnStart.FlatAppearance.BorderSize = 0;
            btnStart.Bounds                   = new Rectangle(20, 374, 185, 46);
            btnStart.Cursor                   = Cursors.Hand;
            btnStart.Click                   += new EventHandler(BtnStart_Click);

            btnStop = new Button();
            btnStop.Text                     = "■  Stop Server";
            btnStop.Font                     = new Font("Segoe UI", 11, FontStyle.Bold);
            btnStop.BackColor                = Color.FromArgb(176, 0, 32);
            btnStop.ForeColor                = Color.White;
            btnStop.FlatStyle                = FlatStyle.Flat;
            btnStop.FlatAppearance.BorderSize = 0;
            btnStop.Bounds                   = new Rectangle(215, 374, 185, 46);
            btnStop.Enabled                  = false;
            btnStop.Cursor                   = Cursors.Hand;
            btnStop.Click                   += new EventHandler(BtnStop_Click);

            btnSetup = new Button();
            btnSetup.Text   = "⚙  Setup (first time only)";
            btnSetup.Font   = new Font("Segoe UI", 10);
            btnSetup.Bounds = new Rectangle(120, 434, 210, 34);
            btnSetup.Cursor = Cursors.Hand;
            btnSetup.Click += new EventHandler(BtnSetup_Click);

            lblNote = new Label();
            lblNote.Text      = "After first run, Setup only needed if dependencies change.";
            lblNote.Font      = new Font("Segoe UI", 8);
            lblNote.ForeColor = Color.DimGray;
            lblNote.Bounds    = new Rectangle(10, 476, 430, 18);
            lblNote.TextAlign = ContentAlignment.MiddleCenter;

            this.Controls.AddRange(new Control[] {
                lblTitle, lblStatus, lblIp, picQr,
                lblQrHint, btnStart, btnStop, btnSetup, lblNote
            });

            this.FormClosing += new FormClosingEventHandler(OnFormClosing);
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private string GetLocalIp()
        {
            try
            {
                Socket s = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
                s.Connect("8.8.8.8", 80);
                string ip = ((IPEndPoint)s.LocalEndPoint).Address.ToString();
                s.Close();
                return ip;
            }
            catch { return "Unknown"; }
        }

        private void SafeInvoke(Action a)
        {
            if (IsDisposed || !IsHandleCreated) return;
            try { if (InvokeRequired) Invoke(a); else a(); }
            catch { }
        }

        // ── Button handlers ───────────────────────────────────────────────────────

        private void BtnStart_Click(object sender, EventArgs e)
        {
            btnStart.Enabled = false;
            btnStop.Enabled  = false;
            _waitCancelled   = false;

            Thread t = new Thread(delegate()
            {
                SafeInvoke(delegate() {
                    lblStatus.Text      = "⏳ Cleaning up old servers...";
                    lblStatus.ForeColor = Color.DarkOrange;
                });

                KillPort(8081); KillPort(8765); KillPort(8766);
                Thread.Sleep(600);

                SafeInvoke(delegate() {
                    lblStatus.Text = "⏳ Starting servers...";
                });

                procWhisper = LaunchWhisperServer();   // Vulkan GPU whisper.cpp
                procBridge  = LaunchScript("bridge_server.py");
                procWake    = LaunchScript("wake_bridge.py");

                // Enable Stop immediately so user is never stuck
                SafeInvoke(delegate() { btnStop.Enabled = true; });

                // Poll until Whisper HTTP is responding (model loaded)
                int elapsed = 0;
                while (!_waitCancelled)
                {
                    Thread.Sleep(1000);
                    elapsed++;

                    if (IsWhisperReady())
                    {
                        SafeInvoke(delegate() {
                            lblStatus.Text      = "● Servers running";
                            lblStatus.ForeColor = Color.FromArgb(46, 125, 50);
                        });
                        ShowQrCode();   // runs Python, updates picQr via SafeInvoke
                        return;
                    }

                    if (elapsed >= 180)
                    {
                        SafeInvoke(delegate() {
                            lblStatus.Text      = "⚠ Model load timed out — run Setup";
                            lblStatus.ForeColor = Color.OrangeRed;
                        });
                        return;
                    }

                    int sec = elapsed;
                    SafeInvoke(delegate() {
                        lblStatus.Text      = string.Format("⏳ Loading AI model... {0}s", sec);
                        lblStatus.ForeColor = Color.DarkOrange;
                    });
                }
            });
            t.IsBackground = true;
            t.Start();
        }

        private void BtnStop_Click(object sender, EventArgs e)
        {
            _waitCancelled   = true;
            btnStop.Enabled  = false;
            btnStart.Enabled = false;
            lblStatus.Text      = "⏳ Stopping servers...";
            lblStatus.ForeColor = Color.DarkOrange;

            Thread t = new Thread(delegate()
            {
                StopAllServers();
                SafeInvoke(delegate() {
                    lblStatus.Text      = "● Server stopped";
                    lblStatus.ForeColor = Color.Gray;
                    btnStart.Enabled    = true;
                    ClearQrCode();
                });
            });
            t.IsBackground = true;
            t.Start();
        }

        private void BtnSetup_Click(object sender, EventArgs e)
        {
            string bat = Path.Combine(appDir, "setup.bat");
            if (!File.Exists(bat))
            {
                MessageBox.Show("setup.bat not found.", "GupTa Server", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName         = "cmd.exe";
                psi.Arguments        = "/k \"" + bat + "\"";
                psi.WorkingDirectory = appDir;
                psi.UseShellExecute  = true;
                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Setup failed:\n" + ex.Message, "GupTa Server",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void OnFormClosing(object sender, FormClosingEventArgs e)
        {
            _waitCancelled = true;
            StopAllServers();   // blocking — ensures servers stop before window closes
        }

        // ── Server lifecycle ──────────────────────────────────────────────────────

        private Process LaunchWhisperServer()
        {
            try
            {
                string exe   = Path.Combine(whisperDir, "whisper-server.exe");
                string model = Path.Combine(whisperDir, "ggml-large-v3-turbo.bin");
                if (!File.Exists(exe) || !File.Exists(model))
                {
                    SafeInvoke(delegate() {
                        MessageBox.Show(
                            "Vulkan Whisper not found. Expected:\n" + exe + "\n" + model,
                            "GupTa Server", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    });
                    return null;
                }

                string logDir = Path.Combine(
                    Environment.GetEnvironmentVariable("TEMP"), "GupTaServer");
                Directory.CreateDirectory(logDir);
                string logPath = Path.Combine(logDir, "whisper.log");

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName         = exe;
                // GPU is automatic with the Vulkan build; flash-attn on by default.
                psi.Arguments        = "-m \"" + model + "\" --host 0.0.0.0 --port 8081 -t 6";
                psi.WorkingDirectory = whisperDir;   // so ggml-*.dll / whisper.dll resolve
                psi.UseShellExecute  = false;
                psi.CreateNoWindow   = true;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError  = true;

                Process p = new Process();
                p.StartInfo = psi;
                StreamWriter logWriter = new StreamWriter(logPath, false);
                logWriter.AutoFlush = true;
                object logLock = new object();
                DataReceivedEventHandler sink = delegate(object s, DataReceivedEventArgs e) {
                    if (e.Data != null) { lock (logLock) { try { logWriter.WriteLine(e.Data); } catch { } } }
                };
                p.OutputDataReceived += sink;
                p.ErrorDataReceived  += sink;
                p.EnableRaisingEvents = true;
                p.Exited += delegate(object s, EventArgs e) { try { logWriter.Close(); } catch { } };
                p.Start();
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();
                return p;
            }
            catch (Exception ex)
            {
                SafeInvoke(delegate() {
                    MessageBox.Show("Failed to start Whisper (Vulkan):\n" + ex.Message,
                        "GupTa Server", MessageBoxButtons.OK, MessageBoxIcon.Error);
                });
                return null;
            }
        }

        private Process LaunchScript(string script)
        {
            try
            {
                // Logs go to %TEMP%\GupTaServer (Program Files isn't writable)
                string logDir = Path.Combine(
                    Environment.GetEnvironmentVariable("TEMP"), "GupTaServer");
                Directory.CreateDirectory(logDir);
                string logPath = Path.Combine(
                    logDir, Path.GetFileNameWithoutExtension(script) + ".log");

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName         = pythonExe;
                psi.Arguments        = "-u \"" + Path.Combine(appDir, script) + "\"";
                psi.WorkingDirectory = appDir;
                psi.UseShellExecute  = false;   // child of THIS process — safe to kill
                psi.CreateNoWindow   = true;
                // Force UTF-8 so arrows / non-English transcriptions never crash on cp1252
                psi.EnvironmentVariables["PYTHONIOENCODING"] = "utf-8";
                psi.EnvironmentVariables["PYTHONUNBUFFERED"] = "1";
                // Redirect output to a log file (also keeps stdout from a dead handle)
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError  = true;

                Process p = new Process();
                p.StartInfo = psi;
                StreamWriter logWriter = new StreamWriter(logPath, false);
                logWriter.AutoFlush = true;
                object logLock = new object();
                DataReceivedEventHandler sink = delegate(object s, DataReceivedEventArgs e) {
                    if (e.Data != null) { lock (logLock) { try { logWriter.WriteLine(e.Data); } catch { } } }
                };
                p.OutputDataReceived += sink;
                p.ErrorDataReceived  += sink;
                p.EnableRaisingEvents = true;
                p.Exited += delegate(object s, EventArgs e) { try { logWriter.Close(); } catch { } };
                p.Start();
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();
                return p;
            }
            catch (Exception ex)
            {
                SafeInvoke(delegate() {
                    MessageBox.Show("Failed to start " + script + ":\n" + ex.Message,
                        "GupTa Server", MessageBoxButtons.OK, MessageBoxIcon.Error);
                });
                return null;
            }
        }

        private void StopAllServers()
        {
            // Kill tracked process objects first (fastest, most precise)
            Process[] procs = new Process[] { procWhisper, procBridge, procWake };
            procWhisper = null;
            procBridge  = null;
            procWake    = null;

            foreach (Process p in procs)
            {
                if (p == null) continue;
                try { if (!p.HasExited) p.Kill(); } catch { }
            }
            foreach (Process p in procs)
            {
                if (p == null) continue;
                try { p.WaitForExit(2000); p.Dispose(); } catch { }
            }

            // Belt-and-suspenders: also kill any remaining listeners on those ports
            KillPort(8081);
            KillPort(8765);
            KillPort(8766);
        }

        private void KillPort(int port)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName        = "cmd.exe";
                psi.Arguments       = string.Format(
                    "/c for /f \"tokens=5\" %a in ('netstat -aon ^| findstr \":{0} \" ^| findstr LISTENING') do taskkill /f /pid %a",
                    port);
                psi.UseShellExecute = false;
                psi.CreateNoWindow  = true;
                using (Process p = Process.Start(psi))
                    if (p != null) p.WaitForExit(5000);
            }
            catch { }
        }

        private bool IsWhisperReady()
        {
            // whisper.cpp only opens the port AFTER the model finishes loading,
            // so a successful TCP connect = model ready. Backend-agnostic.
            try
            {
                using (TcpClient c = new TcpClient())
                {
                    IAsyncResult ar = c.BeginConnect("127.0.0.1", 8081, null, null);
                    bool ok = ar.AsyncWaitHandle.WaitOne(1500);
                    if (ok) { c.EndConnect(ar); return true; }
                    return false;
                }
            }
            catch { return false; }
        }

        // ── QR code display ───────────────────────────────────────────────────────

        private void ShowQrCode()
        {
            try
            {
                // Generate the QR straight from the server's own security module
                // (gen_qr.py with no args calls security.qr_string). This guarantees
                // the QR's token+key are EXACTLY what the bridge validates/decrypts —
                // no separate JSON parsing that could encode a stale/mismatched key.
                string genScript = Path.Combine(appDir, "gen_qr.py");
                if (!File.Exists(genScript)) return;

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName               = pythonExe;
                psi.Arguments              = "\"" + genScript + "\"";
                psi.WorkingDirectory       = appDir;
                psi.UseShellExecute        = false;
                psi.CreateNoWindow         = true;
                psi.RedirectStandardOutput = true;

                string output;
                using (Process p = Process.Start(psi))
                {
                    output = p.StandardOutput.ReadToEnd().Trim();
                    p.WaitForExit(15000);
                }

                if (string.IsNullOrEmpty(output)) return;

                string[] rows = output.Replace("\r", "").Split('\n');
                int size = rows[0].Trim().Length;
                if (size <= 0) return;

                int scale   = Math.Max(2, 218 / size);
                int bmpSize = size * scale;
                Bitmap bmp  = new Bitmap(bmpSize, bmpSize);

                using (Graphics g = Graphics.FromImage(bmp))
                {
                    g.Clear(Color.White);
                    for (int r = 0; r < rows.Length; r++)
                    {
                        string line = rows[r].Trim();
                        for (int c = 0; c < Math.Min(line.Length, size); c++)
                        {
                            if (line[c] == '1')
                                g.FillRectangle(Brushes.Black, c * scale, r * scale, scale, scale);
                        }
                    }
                }

                SafeInvoke(delegate() {
                    if (picQr.Image != null) { picQr.Image.Dispose(); picQr.Image = null; }
                    picQr.Image              = bmp;
                    lblQrPlaceholder.Visible = false;
                });
            }
            catch { }
        }

        private void ClearQrCode()
        {
            if (picQr.Image != null) { picQr.Image.Dispose(); picQr.Image = null; }
            lblQrPlaceholder.Visible = true;
        }

        private string ExtractJson(string json, string key)
        {
            string search = "\"" + key + "\"";
            int i = json.IndexOf(search);
            if (i < 0) return "";
            i = json.IndexOf(":", i + search.Length);
            if (i < 0) return "";
            i = json.IndexOf("\"", i + 1);
            if (i < 0) return "";
            int e = json.IndexOf("\"", i + 1);
            if (e < 0) return "";
            return json.Substring(i + 1, e - i - 1);
        }

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new ServerGUI());
        }
    }
}
