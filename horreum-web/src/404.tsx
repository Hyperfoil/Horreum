import { Link } from "react-router-dom";
 

function NotFound() {
  return (
    
    <div className="not-found-container">
      <h1 className="title">404 Not Found</h1>
      <p className="">The page you are looking for does not exist.</p>
      <Link to="/" className="homeBtn">Go to Home</Link>
    </div>
       
  );
}

export default NotFound;
