import { useState, useEffect } from 'react'
import axios from 'axios'
import { Search, Clock, MapPin, User, BookOpen, AlertCircle, Upload, CheckCircle, XCircle } from 'lucide-react'

export default function App() {
  const [activeTab, setActiveTab] = useState('student')
  
  // Student Portal State
  const [regNumber, setRegNumber] = useState('AU/2023/0009034')
  const [rollNumber, setRollNumber] = useState('UG/02/BTCSEAIML/2023/015')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [data, setData] = useState<any>(null)

  // Upload State
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadResult, setUploadResult] = useState<any>(null)

  // Admin Data State
  const [students, setStudents] = useState<any[]>([])
  const [sections, setSections] = useState<any[]>([])
  const [selectedSection, setSelectedSection] = useState('')
  const [adminTimetable, setAdminTimetable] = useState<any>(null)

  // Add/Edit Student State
  const [newStudent, setNewStudent] = useState({
    first_name: '', last_name: '', registration_number: '', roll_number: '', section_id: ''
  })
  const [editingStudentId, setEditingStudentId] = useState<number | null>(null)
  const [addStudentStatus, setAddStudentStatus] = useState({ loading: false, error: null as string | null, success: false })

  useEffect(() => {
    if (activeTab === 'admin') {
      fetchAdminData()
    }
  }, [activeTab])

  const fetchAdminData = async () => {
    try {
      const [studentsRes, sectionsRes] = await Promise.all([
        axios.get('http://localhost:8000/api/admin/students'),
        axios.get('http://localhost:8000/api/admin/sections')
      ])
      setStudents(studentsRes.data)
      setSections(sectionsRes.data)
      if (sectionsRes.data.length > 0) {
        setSelectedSection(sectionsRes.data[0].id.toString())
        fetchTimetableForSection(sectionsRes.data[0].id)
      }
    } catch (err) {
      console.error("Error fetching admin data", err)
    }
  }

  const fetchTimetableForSection = async (sectionId: string | number) => {
    try {
      const res = await axios.get(`http://localhost:8000/api/admin/timetable/${sectionId}`)
      setAdminTimetable(res.data)
    } catch (err) {
      console.error("Error fetching timetable", err)
    }
  }

  const handleSectionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value
    setSelectedSection(val)
    fetchTimetableForSection(val)
  }

  const handleAddStudent = async (e: React.FormEvent) => {
    e.preventDefault()
    setAddStudentStatus({ loading: true, error: null, success: false })
    try {
      if (editingStudentId) {
        await axios.put(`http://localhost:8000/api/admin/students/${editingStudentId}`, newStudent)
      } else {
        await axios.post('http://localhost:8000/api/admin/students', newStudent)
      }
      setAddStudentStatus({ loading: false, error: null, success: true })
      setNewStudent({ first_name: '', last_name: '', registration_number: '', roll_number: '', section_id: sections.length > 0 ? sections[0].id.toString() : '' })
      setEditingStudentId(null)
      fetchAdminData() // Refresh the table
      setTimeout(() => setAddStudentStatus(s => ({ ...s, success: false })), 3000)
    } catch (err: any) {
      setAddStudentStatus({ loading: false, error: err.response?.data?.detail || "Failed to save student", success: false })
    }
  }

  const handleEditClick = (student: any) => {
    setEditingStudentId(student.id)
    const section = sections.find(s => s.name === student.section_name && s.program_name === student.program_name)
    setNewStudent({
      first_name: student.first_name || '',
      last_name: student.last_name || '',
      registration_number: student.registration_number,
      roll_number: student.roll_number,
      section_id: section ? section.id.toString() : ''
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleCancelEdit = () => {
    setEditingStudentId(null)
    setNewStudent({ first_name: '', last_name: '', registration_number: '', roll_number: '', section_id: sections.length > 0 ? sections[0].id.toString() : '' })
    setAddStudentStatus({ loading: false, error: null, success: false })
  }

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!file) return

    setUploading(true)
    setUploadResult(null)
    setError('')

    const formData = new FormData()
    formData.append('file', file)

    try {
      const response = await axios.post('http://localhost:8000/api/timetable/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      setUploadResult(response.data)
    } catch (err: any) {
      setError(err.response?.data?.detail || 'An error occurred during upload.')
    } finally {
      setUploading(false)
    }
  }

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setData(null)

    try {
      const response = await axios.post('http://localhost:8000/api/student/lookup', {
        registration_number: regNumber,
        roll_number: rollNumber
      })
      if (response.data.success) {
        setData(response.data)
      } else {
        setError(response.data.error || 'Student not found')
      }
    } catch (err: any) {
      setError(err.response?.data?.detail || 'An error occurred while fetching data.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-5xl mx-auto space-y-8">
        
        {/* Header */}
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-gray-900 sm:text-4xl">
            AU Student Timetable
          </h1>
          <p className="mt-3 max-w-2xl mx-auto text-xl text-gray-500 sm:mt-4">
            Intelligence System
          </p>
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200">
          <nav className="-mb-px flex space-x-8" aria-label="Tabs">
            <button
              onClick={() => setActiveTab('student')}
              className={`${activeTab === 'student' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'} whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
            >
              Student Portal
            </button>
            <button
              onClick={() => setActiveTab('admin')}
              className={`${activeTab === 'admin' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'} whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
            >
              Admin: Data & Upload
            </button>
          </nav>
        </div>

        {/* ================= STUDENT PORTAL ================= */}
        {activeTab === 'student' && (
          <div className="space-y-8 animate-in fade-in duration-500">
            {/* Search Card */}
            <div className="bg-white shadow sm:rounded-lg">
              <div className="px-4 py-5 sm:p-6">
                <h3 className="text-lg leading-6 font-medium text-gray-900">Check My Classes</h3>
                <div className="mt-2 max-w-xl text-sm text-gray-500">
                  <p>Enter your registration number and roll number.</p>
                </div>
                <form onSubmit={handleSearch} className="mt-5 sm:flex sm:items-center space-y-3 sm:space-y-0 sm:space-x-3">
                  <div className="w-full sm:max-w-xs">
                    <label htmlFor="reg" className="sr-only">Registration</label>
                    <input
                      type="text"
                      name="reg"
                      id="reg"
                      className="shadow-sm focus:ring-blue-500 focus:border-blue-500 block w-full sm:text-sm border-gray-300 rounded-md p-2 border"
                      placeholder="Registration Number"
                      value={regNumber}
                      onChange={(e) => setRegNumber(e.target.value)}
                      required
                    />
                  </div>
                  <div className="w-full sm:max-w-xs">
                    <label htmlFor="roll" className="sr-only">Roll Number</label>
                    <input
                      type="text"
                      name="roll"
                      id="roll"
                      className="shadow-sm focus:ring-blue-500 focus:border-blue-500 block w-full sm:text-sm border-gray-300 rounded-md p-2 border"
                      placeholder="Roll Number"
                      value={rollNumber}
                      onChange={(e) => setRollNumber(e.target.value)}
                      required
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={loading}
                    className="mt-3 w-full inline-flex items-center justify-center px-4 py-2 border border-transparent shadow-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 sm:mt-0 sm:w-auto sm:text-sm disabled:opacity-50"
                  >
                    {loading ? 'Searching...' : 'Search'}
                  </button>
                </form>
              </div>
            </div>

            {error && (
              <div className="rounded-md bg-red-50 p-4">
                <div className="flex">
                  <div className="flex-shrink-0">
                    <AlertCircle className="h-5 w-5 text-red-400" aria-hidden="true" />
                  </div>
                  <div className="ml-3">
                    <h3 className="text-sm font-medium text-red-800">Error</h3>
                    <div className="mt-2 text-sm text-red-700">
                      <p>{error}</p>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {data && (
              <div className="space-y-8 animate-in fade-in duration-500">
                {/* Student Info */}
                <div className="bg-white shadow overflow-hidden sm:rounded-lg">
                  <div className="px-4 py-5 sm:px-6 flex justify-between items-center">
                    <div>
                      <h3 className="text-lg leading-6 font-medium text-gray-900">Student Information</h3>
                      <p className="mt-1 max-w-2xl text-sm text-gray-500">Personal details and current semester.</p>
                    </div>
                    <User className="h-8 w-8 text-gray-400" />
                  </div>
                  <div className="border-t border-gray-200 px-4 py-5 sm:p-0">
                    <dl className="sm:divide-y sm:divide-gray-200">
                      <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                        <dt className="text-sm font-medium text-gray-500">Full Name</dt>
                        <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{data.student.first_name || 'N/A'} {data.student.last_name || ''}</dd>
                      </div>
                      <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                        <dt className="text-sm font-medium text-gray-500">Registration & Roll Number</dt>
                        <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{data.student.registration_number} | {data.student.roll_number}</dd>
                      </div>
                      <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                        <dt className="text-sm font-medium text-gray-500">Program</dt>
                        <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{data.student.program}</dd>
                      </div>
                      <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                        <dt className="text-sm font-medium text-gray-500">Batch & Semester</dt>
                        <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">Batch {data.student.batch} | Semester {data.student.semester} | Section {data.student.section}</dd>
                      </div>
                    </dl>
                  </div>
                </div>

                {/* AI Explanation */}
                {data.gemini_explanation && (
                  <div className="bg-blue-50 border-l-4 border-blue-400 p-4 rounded-r-md">
                    <div className="flex">
                      <div className="flex-shrink-0">
                        <span className="text-2xl">✨</span>
                      </div>
                      <div className="ml-3">
                        <p className="text-sm text-blue-700 font-medium">AI Summary</p>
                        <p className="text-sm text-blue-900 mt-1">{data.gemini_explanation}</p>
                      </div>
                    </div>
                  </div>
                )}

                {/* Current & Next Class */}
                <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                  <div className={`bg-white shadow rounded-lg overflow-hidden border-2 ${data.current_class ? 'border-green-500' : 'border-gray-200'}`}>
                    <div className={`px-4 py-5 ${data.current_class ? 'bg-green-50' : 'bg-gray-50'}`}>
                      <h3 className="text-lg leading-6 font-medium text-gray-900 flex items-center">
                        <Clock className={`mr-2 h-5 w-5 ${data.current_class ? 'text-green-500' : 'text-gray-400'}`} />
                        Current Class
                      </h3>
                    </div>
                    <div className="border-t border-gray-200 p-4">
                      {data.current_class ? (
                        <div>
                          <p className="text-xl font-bold text-gray-900">{data.current_class.subject}</p>
                          <p className="text-sm text-gray-500 mt-1">{data.current_class.start_time} - {data.current_class.end_time}</p>
                          {data.current_class.faculty && <p className="text-sm text-gray-600 mt-2 flex items-center"><User className="mr-1 h-4 w-4"/> {data.current_class.faculty}</p>}
                          {data.current_class.room && <p className="text-sm text-gray-600 mt-1 flex items-center"><MapPin className="mr-1 h-4 w-4"/> {data.current_class.room}</p>}
                        </div>
                      ) : (
                        <p className="text-gray-500">No class is currently happening.</p>
                      )}
                    </div>
                  </div>

                  <div className="bg-white shadow rounded-lg overflow-hidden border-2 border-blue-200">
                    <div className="px-4 py-5 bg-blue-50">
                      <h3 className="text-lg leading-6 font-medium text-gray-900 flex items-center">
                        <BookOpen className="mr-2 h-5 w-5 text-blue-500" />
                        Next Class
                      </h3>
                    </div>
                    <div className="border-t border-gray-200 p-4">
                      {data.next_class ? (
                        <div>
                          <p className="text-xl font-bold text-gray-900">{data.next_class.subject}</p>
                          <p className="text-sm text-gray-500 mt-1">{data.next_class.start_time} - {data.next_class.end_time}</p>
                          {data.next_class.faculty && <p className="text-sm text-gray-600 mt-2 flex items-center"><User className="mr-1 h-4 w-4"/> {data.next_class.faculty}</p>}
                          {data.next_class.room && <p className="text-sm text-gray-600 mt-1 flex items-center"><MapPin className="mr-1 h-4 w-4"/> {data.next_class.room}</p>}
                        </div>
                      ) : (
                        <p className="text-gray-500">No more classes scheduled today.</p>
                      )}
                    </div>
                  </div>
                </div>

                {/* Schedule Timeline */}
                {data.today_schedule.length > 0 && (
                  <div className="bg-white shadow overflow-hidden sm:rounded-lg mt-8">
                    <div className="px-4 py-5 border-b border-gray-200">
                      <h3 className="text-lg leading-6 font-medium text-gray-900">Today's Schedule</h3>
                    </div>
                    <ul className="divide-y divide-gray-200">
                      {data.today_schedule.map((item: any, idx: number) => (
                        <li key={idx} className={`p-4 ${item.is_current ? 'bg-green-50 border-l-4 border-green-500' : ''} ${item.is_completed ? 'opacity-50' : ''}`}>
                          <div className="flex justify-between">
                            <div>
                              <p className="text-sm font-medium text-gray-900">{item.subject}</p>
                              <p className="text-sm text-gray-500">{item.faculty || 'Unknown Faculty'}</p>
                            </div>
                            <div className="text-right">
                              <p className="text-sm text-gray-900 font-semibold">{item.start_time} - {item.end_time}</p>
                              <p className="text-sm text-gray-500">{item.room || 'TBD'}</p>
                            </div>
                          </div>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* ================= ADMIN SECTION ================= */}
        {activeTab === 'admin' && (
          <div className="space-y-8 animate-in fade-in duration-500">
            
            {/* Upload Section */}
            <div className="bg-white shadow sm:rounded-lg">
              <div className="px-4 py-5 sm:p-6">
                <h3 className="text-lg leading-6 font-medium text-gray-900">Upload Timetable Image</h3>
                <div className="mt-2 max-w-xl text-sm text-gray-500">
                  <p>Upload a clear image (.jpg, .png) of the timetable. Our OCR pipeline will process the image to automatically populate the database.</p>
                </div>
                
                <form onSubmit={handleUpload} className="mt-5">
                  <div className="flex items-center space-x-4">
                    <input
                      type="file"
                      accept="image/*"
                      onChange={(e) => setFile(e.target.files ? e.target.files[0] : null)}
                      className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 border p-2"
                    />
                    <button
                      type="submit"
                      disabled={uploading || !file}
                      className="inline-flex items-center justify-center px-4 py-2 border border-transparent shadow-sm font-medium rounded-md text-white bg-green-600 hover:bg-green-700 disabled:opacity-50"
                    >
                      {uploading ? 'Processing OCR...' : (
                        <span className="flex items-center"><Upload className="w-4 h-4 mr-2" /> Upload & Process</span>
                      )}
                    </button>
                  </div>
                </form>

                {error && (
                  <div className="mt-6 rounded-md bg-red-50 p-4">
                    <div className="flex">
                      <AlertCircle className="h-5 w-5 text-red-400" />
                      <div className="ml-3">
                        <h3 className="text-sm font-medium text-red-800">Error</h3>
                        <div className="mt-2 text-sm text-red-700">{error}</div>
                      </div>
                    </div>
                  </div>
                )}

                {uploadResult && (
                  <div className="mt-6">
                    <div className={`rounded-md p-4 ${uploadResult.success ? 'bg-green-50' : 'bg-red-50'}`}>
                      <div className="flex">
                        <div className="ml-3 w-full">
                          <h3 className={`text-sm font-medium flex items-center ${uploadResult.success ? 'text-green-800' : 'text-red-800'}`}>
                            {uploadResult.success ? <CheckCircle className="w-5 h-5 mr-2 text-green-500" /> : <XCircle className="w-5 h-5 mr-2 text-red-500" />}
                            {uploadResult.success ? 'Processing Successful' : 'Processing Failed'}
                          </h3>
                          <div className={`mt-2 text-sm ${uploadResult.success ? 'text-green-700' : 'text-red-700'}`}>
                            <p>{uploadResult.error || uploadResult.message}</p>
                            {uploadResult.extracted_data && (
                              <div className="mt-4 bg-gray-900 text-green-400 p-4 rounded-md overflow-x-auto">
                                <pre className="text-xs">
                                  {JSON.stringify(uploadResult.extracted_data, null, 2)}
                                </pre>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Add New Student Form */}
            <div className="bg-white shadow sm:rounded-lg">
              <div className="px-4 py-5 border-b border-gray-200">
                <h3 className="text-lg leading-6 font-medium text-gray-900">{editingStudentId ? 'Edit Student' : 'Add New Student'}</h3>
              </div>
              <div className="px-4 py-5 sm:p-6">
                <form onSubmit={handleAddStudent} className="space-y-4">
                  <div className="grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-2">
                    <div>
                      <label className="block text-sm font-medium text-gray-700">First Name</label>
                      <input type="text" required value={newStudent.first_name} onChange={(e) => setNewStudent({...newStudent, first_name: e.target.value})} className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">Last Name</label>
                      <input type="text" required value={newStudent.last_name} onChange={(e) => setNewStudent({...newStudent, last_name: e.target.value})} className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">Registration Number</label>
                      <input type="text" placeholder="e.g. AU/2023/0009034" required value={newStudent.registration_number} onChange={(e) => setNewStudent({...newStudent, registration_number: e.target.value})} className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">Roll Number</label>
                      <input type="text" placeholder="e.g. UG/02/BTCSEAIML/2023/015" required value={newStudent.roll_number} onChange={(e) => setNewStudent({...newStudent, roll_number: e.target.value})} className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm" />
                    </div>
                    <div className="sm:col-span-2">
                      <label className="block text-sm font-medium text-gray-700">Assign Section</label>
                      <select required value={newStudent.section_id} onChange={(e) => setNewStudent({...newStudent, section_id: e.target.value})} className="mt-1 block w-full bg-white border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm">
                        <option value="">Select a section...</option>
                        {sections.map(sec => (
                          <option key={sec.id} value={sec.id}>{sec.program_name} - Batch {sec.batch_year} - Section {sec.name}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                  
                  <div className="pt-2 flex items-center justify-between">
                    <div>
                      {addStudentStatus.error && <p className="text-sm text-red-600">{addStudentStatus.error}</p>}
                      {addStudentStatus.success && <p className="text-sm text-green-600">Student {editingStudentId ? 'updated' : 'added'} successfully!</p>}
                    </div>
                    <div className="flex space-x-3">
                      {editingStudentId && (
                        <button type="button" onClick={handleCancelEdit} className="inline-flex justify-center py-2 px-4 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500">
                          Cancel
                        </button>
                      )}
                      <button type="submit" disabled={addStudentStatus.loading} className="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500">
                        {addStudentStatus.loading ? 'Saving...' : (editingStudentId ? 'Update Student' : 'Add Student')}
                      </button>
                    </div>
                  </div>
                </form>
              </div>
            </div>

            {/* Database View - Students */}
            <div className="bg-white shadow sm:rounded-lg overflow-hidden">
              <div className="px-4 py-5 border-b border-gray-200">
                <h3 className="text-lg leading-6 font-medium text-gray-900">Registered Students</h3>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Registration</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Roll Number</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Program</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Section</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {students.map(s => (
                      <tr key={s.id}>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{s.first_name || 'N/A'} {s.last_name || ''}</td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{s.registration_number}</td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{s.roll_number}</td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{s.program_name} (Batch {s.batch_year})</td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 font-semibold">{s.section_name}</td>
                        <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                          <button onClick={() => handleEditClick(s)} className="text-blue-600 hover:text-blue-900">Edit</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Database View - Timetable */}
            <div className="bg-white shadow sm:rounded-lg overflow-hidden">
              <div className="px-4 py-5 border-b border-gray-200 flex justify-between items-center">
                <h3 className="text-lg leading-6 font-medium text-gray-900">Section Timetable</h3>
                <select 
                  value={selectedSection}
                  onChange={handleSectionChange}
                  className="mt-1 block w-64 pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm rounded-md border"
                >
                  {sections.map(sec => (
                    <option key={sec.id} value={sec.id}>{sec.program_name} - Batch {sec.batch_year} - Section {sec.name}</option>
                  ))}
                </select>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Day</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Subject</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Faculty</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {adminTimetable && adminTimetable.length > 0 ? (
                      adminTimetable.map((t: any, idx: number) => {
                        const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
                        return (
                          <tr key={idx}>
                            <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{days[t.day_of_week]}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{t.start_time} - {t.end_time}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{t.subject}</td>
                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{t.faculty} <br/><span className="text-xs text-gray-400">{t.room}</span></td>
                          </tr>
                        )
                      })
                    ) : (
                      <tr><td colSpan={4} className="px-6 py-4 text-center text-sm text-gray-500">No classes scheduled.</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

          </div>
        )}
      </div>
    </div>
  )
}
