import { useState } from 'react'
import axios from 'axios'
import { Search, Clock, MapPin, User, BookOpen, AlertCircle } from 'lucide-react'

export default function App() {
  const [regNumber, setRegNumber] = useState('AU 2023 000916')
  const [rollNumber, setRollNumber] = useState('UG 02 BTCSC AI-ML 2023/024')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [data, setData] = useState<any>(null)

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
      <div className="max-w-4xl mx-auto space-y-8">
        
        {/* Header */}
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-gray-900 sm:text-4xl">
            AU Student Timetable
          </h1>
          <p className="mt-3 max-w-2xl mx-auto text-xl text-gray-500 sm:mt-4">
            Intelligence System
          </p>
        </div>

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
                <ul role="list" className="divide-y divide-gray-200">
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
    </div>
  )
}
